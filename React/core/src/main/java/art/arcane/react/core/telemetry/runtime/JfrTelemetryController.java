package art.arcane.react.core.telemetry.runtime;

import art.arcane.react.core.controller.SampleController;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The JFR half of Bukkit's runtime telemetry for servers without Folia regions. It shares Bukkit's
 * {@code runtime-telemetry} config id and keys; {@link #refresh} runs once a second off the server thread.
 */
@ConfigDescription("JFR captures protocol traffic totals, chunk generation stages, storage activity, and waits above the configured threshold.")
public final class JfrTelemetryController implements IController {
  @ConfigDoc(value = "Enable selective JFR telemetry. Disabled by default. Published summaries omit addresses, file paths, and stack traces.", impact = "Adds JFR recording work with 30-second and 64-MiB retention limits. Aggregate series reset each second.")
  private boolean jfrEnabled = false;
  @ConfigDoc(value = "Minimum duration in milliseconds for JVM file IO, monitor entry, monitor wait, and thread park events, clamped from 1 to 1000. Chunk generation stages use no duration threshold.", impact = "Lower thresholds emit more JVM events. These metrics describe only events meeting this threshold.")
  private long jfrSlowThresholdMs = 10L;
  private transient final JfrSession jfr = new JfrSession();
  private transient final AtomicLong revision = new AtomicLong();
  private transient final List<String> registeredIds = new ArrayList<>();
  private transient long appliedRevision = -1L;
  private transient volatile boolean active;

  @Override
  public String getId() {
    return "runtime-telemetry";
  }

  @Override
  public String getName() {
    return "Runtime Telemetry";
  }

  @Override
  public void start() {
    active = true;
    appliedRevision = -1L;
    jfr.activate();
    jfr.reset();
  }

  @Override
  public void postStart() {
  }

  public void register(SampleController controller) {
    for (RuntimeMetricSampler.Definition definition : JfrTelemetry.definitions()) {
      RuntimeMetricSampler sampler = new RuntimeMetricSampler(definition, () -> metric(definition.id()));
      if (controller.registerSampler(sampler)) {
        registeredIds.add(sampler.getId());
      }
    }
  }

  @Override
  public void stop() {
    active = false;
    jfr.deactivate();
    jfr.close();
    jfr.reset();
  }

  public void unregister(SampleController controller) {
    if (controller != null) {
      for (String id : registeredIds) {
        controller.unregisterSampler(id);
      }
    }
    registeredIds.clear();
  }

  @Override
  public boolean applyConfigurationSnapshot(Object prepared) {
    if (!IController.super.applyConfigurationSnapshot(prepared)) {
      return false;
    }
    revision.incrementAndGet();
    return true;
  }

  @Override
  public boolean reloadFromDisk(boolean overwriteOnReadFailure) {
    if (!IController.super.reloadFromDisk(overwriteOnReadFailure)) {
      return false;
    }
    revision.incrementAndGet();
    return true;
  }

  public void refresh(long nowMs) {
    if (!active) {
      return;
    }
    long current = revision.get();
    if (current != appliedRevision) {
      appliedRevision = current;
      jfr.configure(jfrEnabled, Math.max(1L, Math.min(jfrSlowThresholdMs, 1_000L)));
    }
    jfr.refresh(nowMs);
  }

  public RuntimeMetric metric(String id) {
    return active ? jfr.metric(id, System.currentTimeMillis()) : RuntimeMetric.unavailable("stopped");
  }

  public JfrTelemetry.Snapshot snapshot() {
    return jfr.snapshot();
  }
}
