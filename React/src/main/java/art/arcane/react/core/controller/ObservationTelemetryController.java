package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.core.telemetry.ObservationMetrics;
import art.arcane.react.core.telemetry.TelemetrySampler;
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
      TelemetrySampler sampler = new TelemetrySampler(new TelemetrySampler.Options(
          counter.id(), counter.displayName(), counter.unit(), Material.COMPARATOR,
          () -> snapshot.values()[counter.ordinal()],
          () -> snapshot.sampledAtMs() > 0L && System.currentTimeMillis() - snapshot.sampledAtMs() < 3_000L,
          TelemetrySampler.Format.DECIMAL, () -> snapshot.sampledAtMs()
      ));
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

  private record Snapshot(double[] values, long sampledAtMs) {
  }
}
