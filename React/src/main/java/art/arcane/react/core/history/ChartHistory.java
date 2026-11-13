package art.arcane.react.core.history;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChartHistory {
  public static final List<String> SAMPLER_IDS = List.of(
      "tick-time",
      "tick-ms-p50",
      "tick-ms-p95",
      "tick-ms-p99",
      "memory-used",
      "memory-free",
      "memory-used-after-gc",
      "disk-read-rate",
      "disk-write-rate",
      "network-receive-rate",
      "network-send-rate",
      "react-async-tick-time",
      "react-sync-tick-time"
  );

  // One slot beyond BUCKETS holds the open bucket, so a read still returns BUCKETS closed buckets.
  private static final int SLOTS = ChartRange.BUCKETS + 1;

  private static final Map<String, Integer> INDEX = index();
  private static final MathContext SIGNIFICANT = new MathContext(4);

  private final Ring[] rings;

  public ChartHistory() {
    ChartRange[] ranges = ChartRange.values();
    rings = new Ring[ranges.length];
    for (ChartRange range : ranges) {
      rings[range.ordinal()] = new Ring(range.stepMs());
    }
  }

  public synchronized void record(MetricSnapshot snapshot) {
    if (snapshot == null) {
      return;
    }
    for (int idIndex = 0; idIndex < SAMPLER_IDS.size(); idIndex++) {
      MetricSnapshotValue value = snapshot.value(SAMPLER_IDS.get(idIndex));
      if (value == null || !value.available() || !Double.isFinite(value.value())) {
        continue;
      }
      for (Ring ring : rings) {
        ring.add(idIndex, snapshot.capturedAtMs(), value.value(), 1L);
      }
    }
  }

  public synchronized void seed(ChartRange range, Map<String, List<HistoryPoint>> points) {
    Ring ring = rings[range.ordinal()];
    for (Map.Entry<String, List<HistoryPoint>> entry : points.entrySet()) {
      Integer idIndex = INDEX.get(entry.getKey());
      if (idIndex == null || entry.getValue() == null) {
        continue;
      }
      for (HistoryPoint point : entry.getValue()) {
        if (point.count() > 0L && Double.isFinite(point.sum())) {
          ring.add(idIndex, point.timestampMs(), point.sum(), point.count());
        }
      }
    }
  }

  public Window read(ChartRange range, List<String> ids, long nowMs) {
    long step = range.stepMs();
    long toMs = Math.floorDiv(nowMs, step) * step;
    long fromMs = toMs - range.spanMs();
    List<String> requested = ids == null || ids.isEmpty() ? SAMPLER_IDS : ids;
    List<String> selected = new ArrayList<>(Math.min(requested.size(), SAMPLER_IDS.size()));
    boolean[] seen = new boolean[SAMPLER_IDS.size()];
    for (String id : requested) {
      Integer idIndex = INDEX.get(id);
      if (idIndex != null && !seen[idIndex]) {
        seen[idIndex] = true;
        selected.add(id);
      }
    }
    double[][] averages = new double[selected.size()][];
    synchronized (this) {
      Ring ring = rings[range.ordinal()];
      for (int index = 0; index < selected.size(); index++) {
        averages[index] = ring.averages(INDEX.get(selected.get(index)), fromMs);
      }
    }
    List<Series> series = new ArrayList<>(selected.size());
    for (int index = 0; index < selected.size(); index++) {
      series.add(new Series(selected.get(index), rounded(averages[index])));
    }
    return new Window(range, fromMs, toMs, List.copyOf(series));
  }

  private static Double[] rounded(double[] averages) {
    Double[] values = new Double[averages.length];
    for (int index = 0; index < averages.length; index++) {
      double average = averages[index];
      if (Double.isFinite(average)) {
        values[index] = BigDecimal.valueOf(average).round(SIGNIFICANT).doubleValue();
      }
    }
    return values;
  }

  private static Map<String, Integer> index() {
    Map<String, Integer> index = new HashMap<>();
    for (int position = 0; position < SAMPLER_IDS.size(); position++) {
      index.put(SAMPLER_IDS.get(position), position);
    }
    return Collections.unmodifiableMap(index);
  }

  public record Window(ChartRange range, long fromMs, long toMs, List<Series> series) {
  }

  public record Series(String id, Double[] values) {
  }

  private static final class Ring {
    private final long stepMs;
    private final double[] sums = new double[SAMPLER_IDS.size() * SLOTS];
    private final int[] counts = new int[SAMPLER_IDS.size() * SLOTS];
    private boolean started;
    private long headMs;

    Ring(long stepMs) {
      this.stepMs = stepMs;
    }

    void add(int idIndex, long timestampMs, double sum, long count) {
      long bucketMs = Math.floorDiv(timestampMs, stepMs) * stepMs;
      if (!started) {
        started = true;
        headMs = bucketMs;
      } else if (bucketMs > headMs) {
        advance(bucketMs);
      } else if (bucketMs < oldestMs()) {
        return;
      }
      int offset = idIndex * SLOTS + slot(bucketMs);
      sums[offset] += sum;
      counts[offset] = (int) Math.min(Integer.MAX_VALUE, counts[offset] + count);
    }

    double[] averages(int idIndex, long fromMs) {
      double[] averages = new double[ChartRange.BUCKETS];
      for (int bucket = 0; bucket < ChartRange.BUCKETS; bucket++) {
        long bucketMs = fromMs + bucket * stepMs;
        averages[bucket] = Double.NaN;
        if (!started || bucketMs > headMs || bucketMs < oldestMs()) {
          continue;
        }
        int offset = idIndex * SLOTS + slot(bucketMs);
        if (counts[offset] > 0) {
          averages[bucket] = sums[offset] / counts[offset];
        }
      }
      return averages;
    }

    private void advance(long bucketMs) {
      long steps = (bucketMs - headMs) / stepMs;
      if (steps >= SLOTS) {
        Arrays.fill(sums, 0D);
        Arrays.fill(counts, 0);
      } else {
        for (long step = 1L; step <= steps; step++) {
          int slot = slot(headMs + step * stepMs);
          for (int idIndex = 0; idIndex < SAMPLER_IDS.size(); idIndex++) {
            sums[idIndex * SLOTS + slot] = 0D;
            counts[idIndex * SLOTS + slot] = 0;
          }
        }
      }
      headMs = bucketMs;
    }

    private long oldestMs() {
      return headMs - (SLOTS - 1L) * stepMs;
    }

    private int slot(long bucketMs) {
      return (int) Math.floorMod(Math.floorDiv(bucketMs, stepMs), (long) SLOTS);
    }
  }
}
