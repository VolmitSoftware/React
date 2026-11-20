package art.arcane.react.core.telemetry.runtime;

import art.arcane.react.platform.PlatformLog;

import java.util.concurrent.atomic.AtomicLong;

/**
 * One optional JFR recording and the samplers' view of it. {@link #configure} replaces the recording,
 * {@link #refresh} publishes its latest snapshot, and {@link #metric} reads a {@code jfr-*} value.
 */
public final class JfrSession {
  private static final long MAX_SNAPSHOT_AGE_MS = 5_000L;
  private final Object lifecycleLock = new Object();
  private final AtomicLong generation = new AtomicLong();
  private volatile JfrTelemetry recording;
  private volatile JfrTelemetry.Snapshot snapshot = JfrTelemetry.Snapshot.EMPTY;
  private volatile String reason = "disabled";
  private volatile boolean active;

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }

  public void configure(boolean enabled, long thresholdMs) {
    close();
    snapshot = JfrTelemetry.Snapshot.EMPTY;
    reason = enabled ? "warming-up" : "disabled";
    if (!enabled) {
      return;
    }
    try {
      long started = generation.get();
      JfrTelemetry recording = JfrTelemetry.start(new JfrTelemetry.Options(thresholdMs), failure -> {
        if (active && started == generation.get()) {
          reason = "sampling-failed";
          PlatformLog.warn("JFR telemetry stream failed", failure);
        }
      });
      synchronized (lifecycleLock) {
        if (active && started == generation.get()) {
          this.recording = recording;
          reason = "";
        } else {
          recording.close();
        }
      }
    } catch (RuntimeException | LinkageError failure) {
      reason = "source-unavailable";
      PlatformLog.warn("Could not start JFR telemetry", failure);
    }
  }

  public void refresh(long nowMs) {
    JfrTelemetry current = recording;
    if (current == null) {
      return;
    }
    try {
      snapshot = current.snapshot(nowMs);
      if (!snapshot.available()) {
        reason = "sampling-failed";
        close();
      }
    } catch (RuntimeException failure) {
      reason = "sampling-failed";
      close();
      PlatformLog.warn("JFR telemetry stopped after an aggregation failure", failure);
    }
  }

  /** Marks the recording failed after its owner could not apply a configuration. */
  public void fail() {
    reason = "sampling-failed";
  }

  public RuntimeMetric metric(String id, long nowMs) {
    if (!reason.isEmpty()) {
      return RuntimeMetric.unavailable(reason);
    }
    JfrTelemetry.Snapshot current = snapshot;
    if (current.sampledAtMs() == 0L || nowMs < current.sampledAtMs() || nowMs - current.sampledAtMs() > MAX_SNAPSHOT_AGE_MS) {
      return RuntimeMetric.unavailable("stale");
    }
    return current.metrics().getOrDefault(id, RuntimeMetric.unavailable("source-unavailable"));
  }

  public JfrTelemetry.Snapshot snapshot() {
    return snapshot;
  }

  public String reason() {
    return reason;
  }

  public void reset() {
    snapshot = JfrTelemetry.Snapshot.EMPTY;
  }

  public void close() {
    JfrTelemetry previous;
    synchronized (lifecycleLock) {
      generation.incrementAndGet();
      previous = recording;
      recording = null;
    }
    if (previous != null) {
      previous.close();
    }
  }
}
