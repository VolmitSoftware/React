package art.arcane.react.core.history;

public record MetricDescriptor(
    String id,
    String name,
    String suffix,
    long firstTimestampMs,
    long lastTimestampMs,
    boolean active
) {
  public MetricDescriptor merge(MetricDescriptor replacement) {
    long first = firstTimestampMs;
    if (first <= 0L) {
      first = replacement.firstTimestampMs;
    } else if (replacement.firstTimestampMs > 0L) {
      first = Math.min(first, replacement.firstTimestampMs);
    }
    long last = Math.max(lastTimestampMs, replacement.lastTimestampMs);
    return new MetricDescriptor(
        id,
        replacement.name == null || replacement.name.isBlank() ? name : replacement.name,
        replacement.suffix == null ? suffix : replacement.suffix,
        first,
        last,
        replacement.active
    );
  }
}
