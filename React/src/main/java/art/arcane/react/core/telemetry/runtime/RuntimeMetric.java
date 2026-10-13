package art.arcane.react.core.telemetry.runtime;

public record RuntimeMetric(double value, long sampledAtMs, boolean available, String unavailableReason) {
  public static RuntimeMetric unavailable(String reason) {
    return new RuntimeMetric(0D, 0L, false, reason);
  }
}
