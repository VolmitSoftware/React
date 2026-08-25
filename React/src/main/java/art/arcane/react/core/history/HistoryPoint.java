package art.arcane.react.core.history;

public record HistoryPoint(
    long timestampMs,
    long intervalMs,
    double first,
    double minimum,
    double maximum,
    double sum,
    double last,
    long count
) {
  public double average() {
    return count <= 0L ? 0D : sum / count;
  }
}
