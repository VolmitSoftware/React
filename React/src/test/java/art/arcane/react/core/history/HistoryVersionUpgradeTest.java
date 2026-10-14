package art.arcane.react.core.history;

import art.arcane.react.api.sampler.Sampler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryVersionUpgradeTest {
  private static final Sampler.Metadata METADATA = new Sampler.Metadata(500L, 500L, Sampler.Quality.MEASURED, "TPS");

  @Test
  void readsCompressedV1AggregatesWithoutInventingSourceTimesOrMetadata(@TempDir Path directory) throws Exception {
    Path path = VersionOneHistoryFixtures.writeRollupSegment(directory);
    byte[] original = Files.readAllBytes(path);

    HistorySegmentCodec.SegmentHeader header = HistorySegmentCodec.readHeader(path);
    HistorySegmentCodec.SegmentCatalog catalog = HistorySegmentCodec.readCatalog(path);
    HistorySegment segment = HistorySegmentCodec.read(path, Set.of("tick-time"));

    assertEquals(HistoryTier.TEN_SECONDS, header.tier());
    assertEquals(1_080, header.bucketCount());
    assertEquals(0L, segment.lastSequence());
    assertEquals(1, catalog.series().size());
    assertEquals("Tick Time", catalog.series().getFirst().name());
    assertEquals("ms", catalog.series().getFirst().suffix());
    assertEquals(0L, catalog.series().getFirst().firstTimestampMs());
    assertEquals(20_000L, catalog.series().getFirst().lastTimestampMs());
    assertEquals(Sampler.Metadata.UNKNOWN, catalog.series().getFirst().metadata());
    HistorySeries series = segment.series("tick-time");
    assertEquals(Sampler.Metadata.UNKNOWN, series.metadata());
    assertEquals(new HistoryPoint(0L, 10_000L, 10D, 5D, 20D, 35D, 20D, 3L, 0L, 0L),
        series.point(0L, 10_000L, 0));
    assertEquals(0L, series.count(1));
    assertEquals(new HistoryPoint(20_000L, 10_000L, 1D, 0.5D, 40.5D, 5_000D, 38.75D, 130L, 0L, 0L),
        series.point(0L, 10_000L, 2));
    assertTrue(HistorySegmentCodec.read(path, Set.of("absent")).series().isEmpty());
    assertArrayEquals(original, Files.readAllBytes(path));
  }

  @Test
  void stillValidatesV1PayloadChecksums(@TempDir Path directory) throws Exception {
    Path path = VersionOneHistoryFixtures.writeRawSegment(directory);
    byte[] bytes = Files.readAllBytes(path);
    bytes[77] ^= 1;
    Files.write(path, bytes);

    IOException failure = assertThrows(IOException.class, () -> HistorySegmentCodec.read(path, null));

    assertTrue(failure.getMessage().contains("checksum"), failure.getMessage());
  }

  @Test
  void preservesV1WalAcrossAppendThenUsesCurrentEncodingAfterCheckpoint(@TempDir Path directory) throws Exception {
    Path path = VersionOneHistoryFixtures.writeWal(directory);
    byte[] original = Files.readAllBytes(path);
    try (HistoryWal wal = new HistoryWal(path)) {
      HistoryWal.WalRecovery recovery = wal.open();
      assertV1Recovery(recovery, List.of(20D, 18D, 19D));
      assertArrayEquals(original, Files.readAllBytes(path));
      wal.append(snapshot(44L, 3_000L, 17D));
    }

    try (HistoryWal wal = new HistoryWal(path)) {
      HistoryWal.WalRecovery recovery = wal.open();
      assertV1Recovery(recovery, List.of(20D, 18D, 19D, 17D));
      assertFalse(recovery.truncated());
      wal.reset();
      wal.append(snapshot(45L, 4_000L, 16D));
    }

    try (HistoryWal wal = new HistoryWal(path)) {
      HistoryWal.WalRecovery recovery = wal.open();
      assertFalse(recovery.truncated());
      assertEquals(1, recovery.samples().size());
      HistoryWal.WalSample sample = recovery.samples().getFirst();
      HistoryWal.WalValue value = sample.values().getFirst();
      assertEquals(45L, sample.sequence());
      assertEquals(4_000L, sample.timestampMs());
      assertEquals(16D, value.value());
      assertEquals(3_990L, value.sampledAtMs());
      assertEquals(METADATA, recovery.metrics().get(value.index()).metadata());
      assertEquals(2, ByteBuffer.wrap(Files.readAllBytes(path)).getShort(4));
    }
  }

  @Test
  void recoversV1WalBeforeAnIncompleteTailAndContinuesAppending(@TempDir Path directory) throws Exception {
    Path path = VersionOneHistoryFixtures.writeWal(directory);
    Files.write(path, new byte[]{0, 0, 0, 33, 2}, StandardOpenOption.APPEND);

    assertTailRecovery(path, List.of(20D, 18D, 19D));
  }

  @Test
  void recoversV1WalBeforeACorruptChecksumAndContinuesAppending(@TempDir Path directory) throws Exception {
    Path path = VersionOneHistoryFixtures.writeWal(directory);
    byte[] bytes = Files.readAllBytes(path);
    bytes[bytes.length - 1] ^= 1;
    Files.write(path, bytes);

    assertTailRecovery(path, List.of(20D, 18D));
  }

  @Test
  void queriesAndCompactsMixedVersionsWithoutLosingTheirEnvelopes(@TempDir Path directory) throws Exception {
    Path oldPath = VersionOneHistoryFixtures.writeRawSegment(directory);
    byte[] original = Files.readAllBytes(oldPath);
    HistoryStore store = new HistoryStore(directory, 3);
    store.initialize();
    assertTrue(store.contains(HistoryTier.RAW, 0L));
    assertEquals(Sampler.Metadata.UNKNOWN, store.descriptor("archived").metadata());
    assertEquals(Sampler.Metadata.UNKNOWN, store.descriptor("tps").metadata());

    HistorySegment current = new HistorySegment(HistoryTier.RAW, 900_000L, 900);
    HistorySeries currentSeries = current.series("tps", "TPS", "");
    currentSeries.metadata(METADATA);
    currentSeries.add(1, 16D, 900_990L);
    currentSeries.add(1, 22D, 901_490L);
    current.lastSequence(45L);
    store.write(current);
    HistoryQueryEngine engine = new HistoryQueryEngine(store, (ids, from, to) -> Map.of());
    HistoryQueryResult before = mixedQuery(engine);
    assertEquals(List.of(
        new HistoryPoint(0L, 10_000L, 20D, 19D, 20D, 39D, 19D, 2L, 0L, 0L),
        new HistoryPoint(900_000L, 10_000L, 16D, 16D, 22D, 38D, 22D, 2L, 900_990L, 901_490L)
    ), before.series().getFirst().points());
    assertEquals(List.of(new HistoryPoint(0L, 10_000L, 7.25D, 7.25D, 7.25D, 7.25D, 7.25D, 1L, 0L, 0L)),
        before.series().get(1).points());

    store.compactAll(HistoryTier.TEN_SECONDS.segmentDurationMs());
    assertArrayEquals(original, Files.readAllBytes(oldPath));
    assertEquals(before.series(), mixedQuery(engine).series());
    assertEquals(2, store.prune(HistoryTier.TEN_SECONDS.segmentDurationMs(), Map.of(HistoryTier.RAW, 1L)));
    assertFalse(Files.exists(oldPath));

    HistoryStore reopened = new HistoryStore(directory, 3);
    reopened.initialize();
    assertTrue(reopened.contains(HistoryTier.TEN_SECONDS, 0L));
    assertEquals(Sampler.Metadata.UNKNOWN, reopened.descriptor("archived").metadata());
    assertEquals(METADATA, reopened.descriptor("tps").metadata());
    HistoryQueryEngine reopenedEngine = new HistoryQueryEngine(reopened, (ids, from, to) -> Map.of());
    assertEquals(before.series(), mixedQuery(reopenedEngine).series());
    assertEquals(2, ByteBuffer.wrap(Files.readAllBytes(directory.resolve("10s/0.rht"))).getShort(4));
  }

  private static HistoryQueryResult mixedQuery(HistoryQueryEngine engine) throws IOException {
    return engine.query(List.of("tps", "archived"), 0L, 910_000L, 10_000L, 45L, 910_000L);
  }

  private static void assertTailRecovery(Path path, List<Double> expected) throws Exception {
    try (HistoryWal wal = new HistoryWal(path)) {
      HistoryWal.WalRecovery recovery = wal.open();
      assertTrue(recovery.truncated());
      assertV1Recovery(recovery, expected);
      wal.append(snapshot(44L, 3_000L, 17D));
    }
    try (HistoryWal wal = new HistoryWal(path)) {
      HistoryWal.WalRecovery recovery = wal.open();
      assertFalse(recovery.truncated());
      assertEquals(expected.size() + 1, recovery.samples().size());
      for (int index = 0; index < expected.size(); index++) {
        assertEquals(expected.get(index), recovery.samples().get(index).values().getFirst().value());
      }
      assertEquals(17D, recovery.samples().getLast().values().getFirst().value());
      assertEquals(0L, recovery.samples().getLast().values().getFirst().sampledAtMs());
    }
  }

  private static void assertV1Recovery(HistoryWal.WalRecovery recovery, List<Double> expected) {
    assertEquals(expected.size(), recovery.samples().size());
    assertEquals(1, recovery.metrics().size());
    HistoryWal.WalMetric metric = recovery.metrics().get(7);
    assertNotNull(metric);
    assertEquals("tps", metric.id());
    assertEquals("TPS", metric.name());
    assertEquals("", metric.suffix());
    assertEquals(Sampler.Metadata.UNKNOWN, metric.metadata());
    for (int index = 0; index < expected.size(); index++) {
      HistoryWal.WalSample sample = recovery.samples().get(index);
      assertEquals(41L + index, sample.sequence());
      assertEquals(1, sample.values().size());
      assertEquals(expected.get(index), sample.values().getFirst().value());
      assertEquals(0L, sample.values().getFirst().sampledAtMs());
    }
  }

  private static MetricSnapshot snapshot(long sequence, long timestampMs, double value) {
    return MetricSnapshot.of(sequence, timestampMs, List.of(new MetricSnapshotValue(
        "tps", "TPS", "", value, Double.toString(value), true,
        timestampMs - 10L, METADATA, "", Sampler.Diagnostics.EMPTY)));
  }
}
