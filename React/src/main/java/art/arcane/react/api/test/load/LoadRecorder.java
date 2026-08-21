package art.arcane.react.api.test.load;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LoadRecorder {
  private static final double BYTES_PER_MB = 1048576.0;
  private static final double OOM_FREE_MB = 64.0;
  private static final double WARMUP_FRACTION = 0.15;

  private final long startMillis;
  private final List<Double> msptSamples;
  private final List<Double> tpsSamples;
  private final List<Double> heapUsedMb;
  private final List<Double> liveSetMb;
  private final List<Double> tickGapsMs;
  private long lastSampleNanos;
  private boolean oom;

  public LoadRecorder(long startMillis) {
    this.startMillis = startMillis;
    this.msptSamples = new ArrayList<Double>();
    this.tpsSamples = new ArrayList<Double>();
    this.heapUsedMb = new ArrayList<Double>();
    this.liveSetMb = new ArrayList<Double>();
    this.tickGapsMs = new ArrayList<Double>();
    this.lastSampleNanos = 0L;
    this.oom = false;
  }

  public void sampleTick() {
    long nowNanos = System.nanoTime();
    if (lastSampleNanos != 0L) {
      tickGapsMs.add((nowNanos - lastSampleNanos) / 1_000_000.0);
    }
    lastSampleNanos = nowNanos;

    double mspt = read("tick-time");
    double tps = read("ticks-per-second");
    double usedBytes = read("memory-used");
    double freeBytes = read("memory-free");
    double liveBytes = read("memory-used-after-gc");

    if (!Double.isNaN(mspt)) {
      msptSamples.add(mspt);
    }
    if (!Double.isNaN(tps)) {
      tpsSamples.add(tps);
    }
    if (!Double.isNaN(usedBytes)) {
      heapUsedMb.add(usedBytes / BYTES_PER_MB);
    }
    if (!Double.isNaN(liveBytes) && liveBytes > 0.0) {
      liveSetMb.add(liveBytes / BYTES_PER_MB);
    }
    if (!Double.isNaN(freeBytes) && freeBytes / BYTES_PER_MB < OOM_FREE_MB) {
      oom = true;
    }
  }

  private double read(String id) {
    Sampler sampler = React.sampler(id);
    if (sampler == null) {
      return Double.NaN;
    }
    double value = sampler.sample();
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return Double.NaN;
    }
    return value;
  }

  public LoadSummary summarize() {
    List<Double> mspt = steady(msptSamples);
    List<Double> tps = steady(tpsSamples);
    List<Double> heap = steady(heapUsedMb);
    List<Double> live = steady(liveSetMb);
    List<Double> gaps = steady(tickGapsMs);

    List<Double> leakBasis = live.isEmpty() ? heap : live;
    double heapStart = leakBasis.isEmpty() ? 0.0 : leakBasis.get(0);
    double heapEnd = leakBasis.isEmpty() ? 0.0 : leakBasis.get(leakBasis.size() - 1);
    int exceptions = React.reportedErrorsSince(startMillis);

    return new LoadSummary(
        mspt.size(),
        average(mspt),
        percentile(mspt, 95),
        max(gaps),
        average(tps),
        min(tps),
        heapStart,
        heapEnd,
        max(heap),
        heapMonotonicGrowth(leakBasis),
        oom,
        exceptions
    );
  }

  private List<Double> steady(List<Double> values) {
    int n = values.size();
    if (n < 8) {
      return new ArrayList<Double>(values);
    }
    int warmup = (int) Math.floor(n * WARMUP_FRACTION);
    return new ArrayList<Double>(values.subList(warmup, n));
  }

  private boolean heapMonotonicGrowth(List<Double> heap) {
    int n = heap.size();
    if (n < 8) {
      return false;
    }
    int firstQuarterEnd = n / 4;
    int lastQuarterStart = (n * 3) / 4;
    double firstMax = 0.0;
    for (int i = 0; i < firstQuarterEnd; i++) {
      firstMax = Math.max(firstMax, heap.get(i));
    }
    double lastMin = Double.MAX_VALUE;
    for (int i = lastQuarterStart; i < n; i++) {
      lastMin = Math.min(lastMin, heap.get(i));
    }
    return lastMin > firstMax;
  }

  private static double average(List<Double> values) {
    if (values.isEmpty()) {
      return 0.0;
    }
    double sum = 0.0;
    for (Double value : values) {
      sum += value;
    }
    return sum / values.size();
  }

  private static double min(List<Double> values) {
    if (values.isEmpty()) {
      return 0.0;
    }
    double smallest = Double.MAX_VALUE;
    for (Double value : values) {
      smallest = Math.min(smallest, value);
    }
    return smallest;
  }

  private static double max(List<Double> values) {
    if (values.isEmpty()) {
      return 0.0;
    }
    double largest = 0.0;
    for (Double value : values) {
      largest = Math.max(largest, value);
    }
    return largest;
  }

  private static double percentile(List<Double> values, int percentile) {
    if (values.isEmpty()) {
      return 0.0;
    }
    List<Double> sorted = new ArrayList<Double>(values);
    Collections.sort(sorted);
    int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
    if (index < 0) {
      index = 0;
    }
    if (index >= sorted.size()) {
      index = sorted.size() - 1;
    }
    return sorted.get(index);
  }
}
