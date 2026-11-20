package art.arcane.react.core.telemetry;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.platform.PlatformLog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The latest host telemetry capture and the samplers that read it. One thread calls {@link #refresh(long)} about once a
 * second; any thread reads {@link #snapshot()}. The provider is built on the first refresh, off the server thread.
 */
public final class HostTelemetryFeed {
  private static final long FAILURE_LOG_INTERVAL_MS = 10_000L;

  private final Supplier<Source> sources;
  private final AtomicReference<HostTelemetrySnapshot> snapshot = new AtomicReference<>(HostTelemetrySnapshot.empty());
  private Source source;
  private long lastFailureLogMs;

  @FunctionalInterface
  public interface Source {
    HostTelemetrySnapshot capture();
  }

  public HostTelemetryFeed(Supplier<Source> sources) {
    this.sources = sources;
  }

  public static HostTelemetryFeed forDataFolder(Path dataPath) {
    return new HostTelemetryFeed(() -> new HostTelemetryProvider(dataPath)::capture);
  }

  public HostTelemetrySnapshot snapshot() {
    return snapshot.get();
  }

  public void refresh(long nowMs) {
    try {
      Source current = source;
      if (current == null) {
        current = sources.get();
        source = current;
      }
      snapshot.set(current.capture());
    } catch (Throwable failure) {
      if (nowMs - lastFailureLogMs >= FAILURE_LOG_INTERVAL_MS) {
        lastFailureLogMs = nowMs;
        PlatformLog.reportError("Failed to refresh cached React host telemetry", failure);
      }
    }
  }

  public void reset() {
    snapshot.set(HostTelemetrySnapshot.empty());
    source = null;
    lastFailureLogMs = 0L;
  }

  /** A sampler for every registered host metric, reading this feed. */
  public List<Sampler> samplers() {
    List<Sampler> samplers = new ArrayList<>();
    for (HostMetric metric : HostMetric.values()) {
      if (metric.definition().registered()) {
        samplers.add(new HostMetricSampler(metric, this::snapshot));
      }
    }
    return samplers;
  }
}
