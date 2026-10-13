package art.arcane.react.core.telemetry;

import art.arcane.react.api.sampler.Sampler;

public record HostReading(double value, long sampledAtMs, boolean available, String unavailableReason) {
  public static final HostReading EMPTY = new HostReading(0D, 0L, false, "warming-up");

  public HostReading {
    if (!Double.isFinite(value)) {
      value = 0D;
      available = false;
    }
    unavailableReason = available ? "" : unavailableReason;
  }

  public HostReading fresh(HostMetric metric, long nowMs) {
    long intervalMs = metric.metadata().intervalMs();
    return available && intervalMs > 0L && nowMs - sampledAtMs > intervalMs * 3L
        ? unavailable("stale") : this;
  }

  public Sampler.Reading reading(HostMetric metric) {
    return new Sampler.Reading(value, sampledAtMs, available, metric.metadata(), unavailableReason);
  }

  public HostReading unavailable(String reason) {
    return new HostReading(value, sampledAtMs, false, reason);
  }
}
