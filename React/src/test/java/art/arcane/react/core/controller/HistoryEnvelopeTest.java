package art.arcane.react.core.controller;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.history.HistoryPoint;
import art.arcane.react.core.history.HistorySegment;
import art.arcane.react.core.history.HistoryStore;
import art.arcane.react.core.history.HistoryTier;
import art.arcane.react.core.history.HistoryWal;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.core.history.MetricSnapshotValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryEnvelopeTest {
  private static final Sampler.Metadata METADATA = new Sampler.Metadata(500L, 500L, Sampler.Quality.MEASURED, "ms");

  @Test
  void retainsBothCapturesAndRecoversWithoutDoubleCounting(@TempDir Path directory) throws Exception {
    HistoryStore store = new HistoryStore(directory, 3);
    store.initialize();
    long start = HistoryTier.RAW.segmentStart(System.currentTimeMillis());
    HistoryController history = new HistoryController();
    history.setHistoryStore(store);
    HistoryWal wal = new HistoryWal(store.walPath());
    wal.open();
    history.setWal(wal);
    try {
      history.persist(snapshot(1L, start + 100L, 10D));
      history.persist(snapshot(2L, start + 600L, 100D));
      assertEnvelope(history.getActiveSegment(), start);
      store.write(history.getActiveSegment());
    } finally {
      wal.close();
    }

    HistoryWal reopened = new HistoryWal(store.walPath());
    HistoryWal.WalRecovery recovery = reopened.open();
    HistoryController recovered = new HistoryController();
    recovered.setHistoryStore(store);
    recovered.setWal(reopened);
    try {
      recovered.recover(recovery);
      assertEnvelope(store.read(HistoryTier.RAW, start, null), start);
      assertEquals(2L, recovered.getSequence().get());
    } finally {
      reopened.close();
    }
  }

  @Test
  void journalOnlyRecoveryPreservesHalfSecondExtrema(@TempDir Path directory) throws Exception {
    HistoryStore store = new HistoryStore(directory, 3);
    store.initialize();
    long start = HistoryTier.RAW.segmentStart(System.currentTimeMillis());
    try (HistoryWal wal = new HistoryWal(store.walPath())) {
      wal.open();
      wal.append(snapshot(1L, start + 100L, 10D));
      wal.append(snapshot(2L, start + 600L, 100D));
    }
    try (HistoryWal wal = new HistoryWal(store.walPath())) {
      HistoryWal.WalRecovery recovery = wal.open();
      HistoryController controller = new HistoryController();
      controller.setHistoryStore(store);
      controller.setWal(wal);
      controller.recover(recovery);
      assertEnvelope(store.read(HistoryTier.RAW, start, null), start);
    }
  }

  private static void assertEnvelope(HistorySegment segment, long start) {
    HistoryPoint point = segment.series("tick-time").point(start, 1_000L, 0);
    assertEquals(10D, point.first());
    assertEquals(10D, point.minimum());
    assertEquals(100D, point.maximum());
    assertEquals(100D, point.last());
    assertEquals(110D, point.sum());
    assertEquals(2L, point.count());
    assertEquals(start + 90L, point.firstSampledAtMs());
    assertEquals(start + 590L, point.lastSampledAtMs());
    assertEquals(METADATA, segment.series("tick-time").metadata());
    assertEquals(2L, segment.lastSequence());
  }

  private static MetricSnapshot snapshot(long sequence, long timestamp, double value) {
    return MetricSnapshot.of(sequence, timestamp, List.of(new MetricSnapshotValue(
        "tick-time", "Tick Time", "ms", value, Double.toString(value), true,
        timestamp - 10L, METADATA, "", Sampler.Diagnostics.EMPTY)));
  }
}
