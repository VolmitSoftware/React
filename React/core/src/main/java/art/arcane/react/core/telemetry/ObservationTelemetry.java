package art.arcane.react.core.telemetry;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.PlatformLog;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Per-second rates of React's own nearby-entity, heatmap, map and entity-scan work, read from {@link ObservationMetrics}.
 */
public final class ObservationTelemetry {
  private final List<String> samplerIds = new ArrayList<>();
  private volatile Snapshot snapshot = new Snapshot(new double[ObservationMetrics.Counter.values().length], 0L);
  private ScheduledExecutorService executor;
  private long previousNanos;

  public void start() {
    ObservationMetrics.drain();
    previousNanos = System.nanoTime();
    snapshot = new Snapshot(new double[ObservationMetrics.Counter.values().length], 0L);
    samplerIds.clear();
  }

  /** Registers the samplers and starts the once-a-second capture; a null controller only starts the capture. */
  public void register(SampleController controller) {
    for (ObservationMetrics.Counter counter : ObservationMetrics.Counter.values()) {
      CounterSampler sampler = new CounterSampler(counter);
      if (controller != null && controller.registerSampler(sampler)) {
        samplerIds.add(counter.id());
      }
    }
    executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "react-observation-telemetry");
      thread.setDaemon(true);
      return thread;
    });
    executor.scheduleWithFixedDelay(this::capture, 1L, 1L, TimeUnit.SECONDS);
  }

  public void stop(SampleController controller) {
    ScheduledExecutorService active = executor;
    executor = null;
    if (active != null) {
      active.shutdownNow();
      try {
        if (!active.awaitTermination(5L, TimeUnit.SECONDS)) {
          PlatformLog.warn("Observation telemetry did not stop within five seconds");
        }
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        PlatformLog.warn("Interrupted while stopping observation telemetry", failure);
      }
    }
    if (controller != null) {
      for (String id : samplerIds) {
        controller.unregisterSampler(id);
      }
    }
    samplerIds.clear();
    snapshot = new Snapshot(new double[ObservationMetrics.Counter.values().length], 0L);
  }

  private void capture() {
    long now = System.nanoTime();
    long elapsed = now - previousNanos;
    previousNanos = now;
    long[] counters = ObservationMetrics.drain();
    double[] values = new double[counters.length];
    for (ObservationMetrics.Counter counter : ObservationMetrics.Counter.values()) {
      values[counter.ordinal()] = counter.rate(counters[counter.ordinal()], elapsed);
    }
    snapshot = new Snapshot(values, System.currentTimeMillis());
  }

  private final class CounterSampler implements Sampler {
    private final ObservationMetrics.Counter counter;
    private volatile boolean active;

    private CounterSampler(ObservationMetrics.Counter counter) {
      this.counter = counter;
    }

    @Override
    public String getId() {
      return counter.id();
    }

    @Override
    public String getName() {
      return counter.displayName();
    }

    @Override
    public String getIcon() {
      return "minecraft:comparator";
    }

    @Override
    public void loadConfiguration() {
    }

    @Override
    public boolean reloadConfiguration() {
      return true;
    }

    @Override
    public double sample() {
      return active ? snapshot.values()[counter.ordinal()] : 0D;
    }

    @Override
    public Reading captureReading() {
      Snapshot current = snapshot;
      long age = System.currentTimeMillis() - current.sampledAtMs();
      boolean available = active && current.sampledAtMs() > 0L && age >= 0L && age < 3_000L;
      String reason = !active ? "stopped" : current.sampledAtMs() <= 0L ? "warming-up" : "stale";
      return reading(current.values()[counter.ordinal()], current.sampledAtMs(), available, reason);
    }

    @Override
    public Metadata metadata() {
      return new Metadata(1_000L, 1_000L, Quality.MEASURED, counter.unit());
    }

    @Override
    public boolean isSampleAvailable() {
      return captureReading().available();
    }

    @Override
    public String formattedValue(double value) {
      return isSampleAvailable() ? Form.f(value, 2) : "---";
    }

    @Override
    public String formattedSuffix(double value) {
      return isSampleAvailable() ? counter.unit() : "";
    }

    @Override
    public void start() {
      active = true;
    }

    @Override
    public void stop() {
      active = false;
    }
  }

  private record Snapshot(double[] values, long sampledAtMs) {
  }
}
