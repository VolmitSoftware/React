package art.arcane.react.core.pluginapi;

import art.arcane.react.core.pluginapi.PluginApiPackDefinition.MetricDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.TransformDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.TransformMode;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class PluginApiMetricRuntime {
  public static final int FAILURE_LIMIT = 5;
  public static final long SLOW_SAMPLE_MS = 50L;
  public static final long SLOW_WARNING_INTERVAL_MS = 60_000L;

  private final MetricDefinition definition;
  private final LongAdder eventCount = new LongAdder();
  private final AtomicLong totalSamples = new AtomicLong();
  private final AtomicLong failedSamples = new AtomicLong();
  private volatile double lastValue;
  private volatile long sampledAtMs;
  private volatile long lastAttemptMs;
  private volatile long lastDurationMs;
  private volatile long lastSlowWarningMs;
  private volatile String availabilityReason = "waiting-for-first-sample";
  private volatile int consecutiveFailures;
  private volatile boolean quarantined;
  private volatile Listener eventListener;
  private double previousRaw;
  private long previousRawAtMs;
  private boolean hasPreviousRaw;

  public PluginApiMetricRuntime(MetricDefinition definition) {
    this.definition = definition;
  }

  public MetricDefinition definition() {
    return definition;
  }

  public boolean due(long nowMs) {
    return !quarantined && nowMs - lastAttemptMs >= definition.sampleEveryMs();
  }

  public void beginAttempt(long nowMs) {
    lastAttemptMs = nowMs;
  }

  public void accept(double rawValue, long sampledAt, long durationMs) {
    lastDurationMs = Math.max(0L, durationMs);
    double transformed = transform(rawValue, sampledAt);
    if (!Double.isFinite(transformed)) {
      unavailable("transform-warming-up", durationMs, false);
      return;
    }
    lastValue = transformed;
    sampledAtMs = sampledAt;
    availabilityReason = "";
    consecutiveFailures = 0;
    totalSamples.incrementAndGet();
  }

  public void unavailable(String reason, long durationMs, boolean failure) {
    lastDurationMs = Math.max(0L, durationMs);
    availabilityReason = reason == null || reason.isBlank() ? "unavailable" : reason;
    if (!failure) {
      return;
    }
    failedSamples.incrementAndGet();
    consecutiveFailures++;
    if (consecutiveFailures >= FAILURE_LIMIT) {
      quarantined = true;
      availabilityReason = "quarantined-after-" + FAILURE_LIMIT + "-failures";
    }
  }

  public boolean available(long nowMs) {
    return !quarantined
        && availabilityReason.isEmpty()
        && sampledAtMs > 0L
        && nowMs - sampledAtMs <= definition.staleAfterMs();
  }

  public double lastValue() {
    return lastValue;
  }

  public long sampledAtMs() {
    return sampledAtMs;
  }

  public long lastDurationMs() {
    return lastDurationMs;
  }

  public String availabilityReason(long nowMs) {
    if (quarantined) {
      return availabilityReason;
    }
    if (availabilityReason.isEmpty() && sampledAtMs > 0L && nowMs - sampledAtMs > definition.staleAfterMs()) {
      return "stale";
    }
    return availabilityReason;
  }

  public long totalSamples() {
    return totalSamples.get();
  }

  public long failedSamples() {
    return failedSamples.get();
  }

  public int consecutiveFailures() {
    return consecutiveFailures;
  }

  public boolean quarantined() {
    return quarantined;
  }

  public boolean shouldWarnSlow(long nowMs) {
    if (nowMs - lastSlowWarningMs < SLOW_WARNING_INTERVAL_MS) {
      return false;
    }
    lastSlowWarningMs = nowMs;
    return true;
  }

  public LongAdder eventCount() {
    return eventCount;
  }

  public Listener eventListener() {
    return eventListener;
  }

  public void eventListener(Listener listener) {
    eventListener = listener;
  }

  public void unregisterEventListener() {
    Listener listener = eventListener;
    eventListener = null;
    if (listener != null) {
      HandlerList.unregisterAll(listener);
    }
  }

  private double transform(double rawValue, long sampledAt) {
    TransformDefinition transform = definition.transform();
    double value = rawValue;
    if (transform.mode() == TransformMode.DELTA_PER_SECOND) {
      if (!hasPreviousRaw || sampledAt <= previousRawAtMs || rawValue < previousRaw) {
        previousRaw = rawValue;
        previousRawAtMs = sampledAt;
        hasPreviousRaw = true;
        return Double.NaN;
      }
      value = (rawValue - previousRaw) * 1_000D / (sampledAt - previousRawAtMs);
      previousRaw = rawValue;
      previousRawAtMs = sampledAt;
    }
    value = (value * transform.scale()) + transform.offset();
    if (transform.minimum() != null) {
      value = Math.max(transform.minimum(), value);
    }
    if (transform.maximum() != null) {
      value = Math.min(transform.maximum(), value);
    }
    return value;
  }
}
