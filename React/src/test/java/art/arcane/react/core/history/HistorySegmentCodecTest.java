package art.arcane.react.core.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistorySegmentCodecTest {
  @Test
  void roundTripsSparseAggregatesAndMetadata(@TempDir Path directory) throws Exception {
    HistorySegment segment = new HistorySegment(HistoryTier.RAW, 0L, 900);
    HistorySeries tick = segment.series("tick-time", "Tick Time", "ms");
    tick.set(0, -0D);
    tick.set(1, 42.25D);
    tick.set(899, 50D);
    HistorySeries tps = segment.series("ticks-per-second", "Ticks Per Second", "");
    tps.set(10, 20D);
    Path file = directory.resolve("0.rht");

    HistorySegmentCodec.write(file, segment, 3);
    HistorySegment decoded = HistorySegmentCodec.read(file, Set.of("tick-time"));
    HistorySegmentCodec.SegmentCatalog catalog = HistorySegmentCodec.readCatalog(file);

    assertEquals(1, decoded.series().size());
    assertEquals(
        Double.doubleToRawLongBits(-0D),
        Double.doubleToRawLongBits(decoded.series("tick-time").last(0))
    );
    assertEquals(42.25D, decoded.series("tick-time").last(1));
    assertEquals(50D, decoded.series("tick-time").last(899));
    assertEquals(2, catalog.series().size());
    assertEquals(0L, catalog.series().get(0).firstTimestampMs());
    assertEquals(899_000L, catalog.series().get(0).lastTimestampMs());
    assertTrue(Files.size(file) < 4_096L);
  }

  @Test
  void rejectsTruncatedSegment(@TempDir Path directory) throws Exception {
    HistorySegment segment = new HistorySegment(HistoryTier.RAW, 0L, 900);
    segment.series("constant", "Constant", "").set(0, 1D);
    Path file = directory.resolve("0.rht");
    HistorySegmentCodec.write(file, segment, 3);
    long size = Files.size(file);
    try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) {
      channel.truncate(size - 1L);
    }

    assertThrows(Exception.class, () -> HistorySegmentCodec.read(file, null));
  }
}
