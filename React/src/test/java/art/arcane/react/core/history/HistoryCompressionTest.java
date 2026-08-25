package art.arcane.react.core.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryCompressionTest {
  private static final int METRICS = 200;
  private static final int BUCKETS = 900;

  @Test
  void realisticRawSegmentIsSmallerThanOneUncompressedDoublePerSample(@TempDir Path directory) throws Exception {
    HistorySegment segment = new HistorySegment(HistoryTier.RAW, 0L, BUCKETS);
    Random random = new Random(4_292_024L);
    for (int metricIndex = 0; metricIndex < METRICS; metricIndex++) {
      HistorySeries series = segment.series("metric-" + metricIndex, "Metric " + metricIndex, "ms");
      double value = 5D + (metricIndex * 0.25D);
      if (metricIndex >= 80 && metricIndex < 160) {
        value = Math.rint(value);
      }
      for (int bucketIndex = 0; bucketIndex < BUCKETS; bucketIndex++) {
        if (metricIndex >= 80 && metricIndex < 160 && random.nextDouble() < 0.12D) {
          value += random.nextBoolean() ? 1D : -1D;
          value = Math.rint(value);
        } else if (metricIndex >= 160) {
          value += (random.nextDouble() - 0.5D) * 0.08D;
          value = Math.rint(value * 1_000D) / 1_000D;
        }
        series.set(bucketIndex, value);
      }
    }
    Path file = directory.resolve("0.rht");

    long startedNanos = System.nanoTime();
    HistorySegmentCodec.write(file, segment, 3);
    long elapsedNanos = System.nanoTime() - startedNanos;
    long storedBytes = Files.size(file);
    long scalarBytes = (long) METRICS * BUCKETS * Double.BYTES;

    System.out.printf(
        "history-compression metrics=%d buckets=%d bytes=%d scalar-bytes=%d ratio=%.4f encode-ms=%.3f%n",
        METRICS,
        BUCKETS,
        storedBytes,
        scalarBytes,
        (double) storedBytes / scalarBytes,
        elapsedNanos / 1_000_000D
    );
    assertTrue(storedBytes < scalarBytes / 4L);
  }
}
