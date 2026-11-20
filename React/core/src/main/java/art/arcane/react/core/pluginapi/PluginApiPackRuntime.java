package art.arcane.react.core.pluginapi;

import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.SourceDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.SourceType;
import art.arcane.react.platform.PlatformLog;

import java.util.ArrayList;
import java.util.List;

public final class PluginApiPackRuntime {
  private final PluginApiPackDefinition definition;
  private final PluginApiHost host;
  private final List<PluginApiMetricRuntime> metrics;
  private final List<String> samplerIds = new ArrayList<>();
  private volatile PackState state = PackState.LOADING;
  private volatile String detail = "loading";
  private volatile String targetVersion = "";

  public PluginApiPackRuntime(PluginApiPackDefinition definition, PluginApiHost host) {
    this.definition = definition;
    this.host = host;
    this.metrics = definition.metrics().stream().map(PluginApiMetricRuntime::new).toList();
  }

  public PluginApiPackDefinition definition() {
    return definition;
  }

  public List<PluginApiMetricRuntime> metrics() {
    return metrics;
  }

  public PackState state() {
    return state;
  }

  public String detail() {
    return detail;
  }

  public String targetVersion() {
    return targetVersion;
  }

  public boolean activate(SampleController controller) {
    if (!definition.enabled()) {
      state = PackState.DISABLED;
      detail = "disabled-by-pack";
      return true;
    }
    if (controller == null || controller.getSamplers() == null) {
      state = PackState.INVALID;
      detail = "sample-controller-unavailable";
      return false;
    }
    for (PluginApiMetricRuntime metric : metrics) {
      if (controller.getSampler(metric.definition().samplerId()) != null) {
        state = PackState.INVALID;
        detail = "sampler-id-conflict:" + metric.definition().samplerId();
        return false;
      }
    }
    for (PluginApiMetricRuntime metric : metrics) {
      String samplerId = metric.definition().samplerId();
      if (!controller.registerSampler(new PluginApiSampler(definition.id(), definition.targetPlugin(), metric))) {
        retire(controller);
        state = PackState.INVALID;
        detail = "sampler-registration-failed:" + samplerId;
        return false;
      }
      samplerIds.add(samplerId);
    }
    state = PackState.LOADING;
    detail = "waiting-for-target";
    return true;
  }

  public void retire(SampleController controller) {
    for (PluginApiMetricRuntime metric : metrics) {
      metric.unregisterEventListener();
    }
    if (controller != null) {
      for (String samplerId : new ArrayList<>(samplerIds)) {
        controller.unregisterSampler(samplerId);
      }
    }
    samplerIds.clear();
  }

  public void collect(long nowMs) {
    if (!definition.enabled()) {
      state = PackState.DISABLED;
      detail = "disabled-by-pack";
      return;
    }
    PluginApiHost.Target target = host.target(definition.targetPlugin());
    if (target == null) {
      String missing = host.targetMissingDetail();
      targetVersion = "";
      state = PackState.TARGET_MISSING;
      detail = missing;
      metrics.forEach(metric -> {
        metric.unregisterEventListener();
        metric.unavailable(missing, 0L, false);
      });
      return;
    }
    targetVersion = target.version();
    if (!PluginApiPackParser.versionMatches(targetVersion, definition.targetVersions())) {
      state = PackState.INCOMPATIBLE;
      detail = "target-version-not-listed:" + targetVersion;
      metrics.forEach(metric -> {
        metric.unregisterEventListener();
        metric.unavailable("target-version-not-listed", 0L, false);
      });
      return;
    }

    int available = 0;
    int quarantined = 0;
    for (PluginApiMetricRuntime metric : metrics) {
      ensureEventListener(metric, target);
      boolean eventReady = metric.definition().source().type() != SourceType.EVENT_COUNTER
          || metric.eventRegistered();
      if (eventReady && metric.due(nowMs)) {
        collectMetric(metric, target, nowMs);
      }
      if (metric.available(nowMs)) {
        available++;
      }
      if (metric.quarantined()) {
        quarantined++;
      }
    }
    if (quarantined == metrics.size()) {
      state = PackState.QUARANTINED;
      detail = "all-metrics-quarantined";
    } else if (available == metrics.size()) {
      state = PackState.HEALTHY;
      detail = "all-metrics-available";
    } else {
      state = PackState.DEGRADED;
      detail = available + "/" + metrics.size() + " metrics available";
    }
  }

  public PackStatus snapshot() {
    long now = System.currentTimeMillis();
    List<MetricStatus> metricStatuses = metrics.stream()
        .map(metric -> new MetricStatus(
            metric.definition().id(),
            metric.definition().samplerId(),
            metric.definition().displayName(),
            metric.definition().source().type().name().toLowerCase().replace('_', '-'),
            metric.available(now),
            metric.availabilityReason(now),
            metric.sampledAtMs(),
            metric.lastDurationMs(),
            metric.totalSamples(),
            metric.failedSamples(),
            metric.quarantined()
        ))
        .toList();
    return new PackStatus(
        definition.id(),
        definition.version(),
        definition.name(),
        definition.authors(),
        definition.targetPlugin(),
        targetVersion,
        definition.targetVersions(),
        definition.enabled(),
        definition.trusted(),
        state,
        detail,
        definition.sourcePath().getFileName().toString(),
        definition.rawContent(),
        metricStatuses
    );
  }

  private void collectMetric(PluginApiMetricRuntime metric, PluginApiHost.Target target, long nowMs) {
    metric.beginAttempt(nowMs);
    long started = System.nanoTime();
    int previousFailures = metric.consecutiveFailures();
    try {
      double value = read(metric.definition().source(), metric, target);
      long durationMs = (System.nanoTime() - started) / 1_000_000L;
      if (!Double.isFinite(value)) {
        metric.unavailable("source-unavailable", durationMs, false);
      } else {
        metric.accept(value, nowMs, durationMs);
      }
      if (durationMs >= PluginApiMetricRuntime.SLOW_SAMPLE_MS && metric.shouldWarnSlow(nowMs)) {
        PlatformLog.warn(host.label() + " metric sampled slowly: pack=" + definition.id()
            + " metric=" + metric.definition().id() + " duration=" + durationMs + "ms");
      }
    } catch (Throwable failure) {
      long durationMs = (System.nanoTime() - started) / 1_000_000L;
      metric.unavailable(failure.getClass().getSimpleName(), durationMs, true);
      if (previousFailures == 0 || metric.quarantined()) {
        PlatformLog.warn(host.label() + " metric sample failed: pack=" + definition.id()
            + " metric=" + metric.definition().id()
            + " source=" + metric.definition().source().type().name().toLowerCase()
            + " failures=" + metric.consecutiveFailures(), failure);
      }
    }
  }

  private double read(SourceDefinition source, PluginApiMetricRuntime metric, PluginApiHost.Target target) throws Exception {
    return switch (source.type()) {
      case INTEGRATION -> host.integration(source.pluginId(), source.key());
      case PLACEHOLDER -> host.placeholder(source.placeholder(), source.foliaSafe());
      case ORAXEN -> PluginApiOraxenReader.read(target.classLoader(), source.key());
      case EVENT_COUNTER -> metric.eventCount().sum();
    };
  }

  private void ensureEventListener(PluginApiMetricRuntime metric, PluginApiHost.Target target) {
    if (metric.definition().source().type() != SourceType.EVENT_COUNTER
        || metric.eventRegistered()
        || metric.quarantined()) {
      return;
    }
    try {
      Runnable unregister = host.eventCounter(target, metric.definition().source().eventClass(), metric.eventCount()::increment);
      if (unregister == null) {
        metric.unavailable("event-counters-unsupported", 0L, false);
        return;
      }
      metric.eventUnregister(unregister);
    } catch (Throwable failure) {
      metric.unavailable("event-registration-failed", 0L, true);
      if (metric.consecutiveFailures() == 1 || metric.quarantined()) {
        PlatformLog.warn(host.label() + " event registration failed: pack=" + definition.id()
            + " metric=" + metric.definition().id()
            + " event=" + metric.definition().source().eventClass(), failure);
      }
    }
  }

  public enum PackState {
    LOADING,
    DISABLED,
    TARGET_MISSING,
    INCOMPATIBLE,
    HEALTHY,
    DEGRADED,
    QUARANTINED,
    INVALID
  }

  public record PackStatus(
      String id,
      String version,
      String name,
      List<String> authors,
      String targetPlugin,
      String targetVersion,
      List<String> targetVersions,
      boolean enabled,
      boolean trusted,
      PackState state,
      String detail,
      String fileName,
      String rawContent,
      List<MetricStatus> metrics
  ) {
  }

  public record MetricStatus(
      String id,
      String samplerId,
      String displayName,
      String sourceType,
      boolean available,
      String availabilityReason,
      long sampledAtMs,
      long sampleDurationMs,
      long acceptedSamples,
      long failedSamples,
      boolean quarantined
  ) {
  }
}
