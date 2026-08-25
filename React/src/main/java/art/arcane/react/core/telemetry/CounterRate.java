package art.arcane.react.core.telemetry;

public final class CounterRate {
  private long previousValue;
  private long previousAtMs;

  public double perMinute(long value, long nowMs) {
    if (previousAtMs == 0L) {
      previousValue = value;
      previousAtMs = nowMs;
      return 0D;
    }
    long elapsedMs = Math.max(0L, nowMs - previousAtMs);
    double rate = value < previousValue ? 0D : HostTelemetryProvider.perMinute(value, previousValue, elapsedMs);
    previousValue = value;
    previousAtMs = nowMs;
    return rate;
  }

  public void reset() {
    previousValue = 0L;
    previousAtMs = 0L;
  }
}
