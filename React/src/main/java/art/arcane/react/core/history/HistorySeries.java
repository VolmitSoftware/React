package art.arcane.react.core.history;

import java.util.Arrays;

public final class HistorySeries {
  private final String id;
  private String name;
  private String suffix;
  private final double[] first;
  private final double[] minimum;
  private final double[] maximum;
  private final double[] sum;
  private final double[] last;
  private final long[] count;

  public HistorySeries(String id, String name, String suffix, int bucketCount) {
    this.id = id;
    this.name = name;
    this.suffix = suffix;
    this.first = new double[bucketCount];
    this.minimum = new double[bucketCount];
    this.maximum = new double[bucketCount];
    this.sum = new double[bucketCount];
    this.last = new double[bucketCount];
    this.count = new long[bucketCount];
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String suffix() {
    return suffix;
  }

  public int bucketCount() {
    return count.length;
  }

  public double first(int index) {
    return first[index];
  }

  public double minimum(int index) {
    return minimum[index];
  }

  public double maximum(int index) {
    return maximum[index];
  }

  public double sum(int index) {
    return sum[index];
  }

  public double last(int index) {
    return last[index];
  }

  public long count(int index) {
    return count[index];
  }

  public void add(int index, double value) {
    if (!Double.isFinite(value)) {
      return;
    }
    if (count[index] == 0L) {
      first[index] = value;
      minimum[index] = value;
      maximum[index] = value;
    } else {
      minimum[index] = Math.min(minimum[index], value);
      maximum[index] = Math.max(maximum[index], value);
    }
    sum[index] += value;
    last[index] = value;
    count[index]++;
  }

  public void set(int index, double value) {
    if (!Double.isFinite(value)) {
      return;
    }
    first[index] = value;
    minimum[index] = value;
    maximum[index] = value;
    sum[index] = value;
    last[index] = value;
    count[index] = 1L;
  }

  public void add(int index, HistoryPoint point) {
    if (point == null || point.count() <= 0L) {
      return;
    }
    if (count[index] == 0L) {
      first[index] = point.first();
      minimum[index] = point.minimum();
      maximum[index] = point.maximum();
    } else {
      minimum[index] = Math.min(minimum[index], point.minimum());
      maximum[index] = Math.max(maximum[index], point.maximum());
    }
    sum[index] += point.sum();
    last[index] = point.last();
    count[index] += point.count();
  }

  public HistoryPoint point(long segmentStartMs, long intervalMs, int index) {
    return new HistoryPoint(
        segmentStartMs + (intervalMs * index),
        intervalMs,
        first[index],
        minimum[index],
        maximum[index],
        sum[index],
        last[index],
        count[index]
    );
  }

  public void metadata(String name, String suffix) {
    if (name != null && !name.isBlank()) {
      this.name = name;
    }
    if (suffix != null) {
      this.suffix = suffix;
    }
  }

  public long[] counts() {
    return Arrays.copyOf(count, count.length);
  }

  double[] firstValues() {
    return first;
  }

  double[] minimumValues() {
    return minimum;
  }

  double[] maximumValues() {
    return maximum;
  }

  double[] sums() {
    return sum;
  }

  double[] lastValues() {
    return last;
  }

  long[] countValues() {
    return count;
  }
}
