package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.core.telemetry.ObservationMetrics;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.volmlib.util.format.Form;
import art.arcane.react.util.plugin.IController;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ObservationTelemetryController implements IController {
  private transient final List<String> samplerIds = new ArrayList<>();
  private transient volatile Snapshot snapshot = new Snapshot(new double[ObservationMetrics.Counter.values().length], 0L);
  private transient ScheduledExecutorService executor;
  private transient long previousNanos;

  @Override
  public String getId() {
    return "observation-telemetry";
  }

  @Override
  public String getName() {
    return "Observation Telemetry";
  }

  @Override
  public void start() {
    ObservationMetrics.drain();
    previousNanos = System.nanoTime();
    snapshot = new Snapshot(new double[ObservationMetrics.Counter.values().length], 0L);
    samplerIds.clear();
  }

  @Override
  public void postStart() {
    SampleController controller = React.controller(SampleController.class);
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

  @Override
  public void stop() {
    ScheduledExecutorService active = executor;
    executor = null;
    if (active != null) {
      active.shutdownNow();
      try {
        if (!active.awaitTermination(5L, TimeUnit.SECONDS)) {
          React.warn("Observation telemetry did not stop within five seconds");
        }
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        React.warn("Interrupted while stopping observation telemetry", failure);
      }
    }
    SampleController controller = React.controller(SampleController.class);
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
    public Material getIcon() {
      return Material.COMPARATOR;
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
