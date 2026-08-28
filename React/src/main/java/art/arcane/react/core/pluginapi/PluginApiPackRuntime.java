package art.arcane.react.core.pluginapi;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.MetricDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.SourceDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.SourceType;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class PluginApiPackRuntime {
  private final PluginApiPackDefinition definition;
  private final List<PluginApiMetricRuntime> metrics;
  private final List<String> samplerIds = new ArrayList<>();
  private volatile PackState state = PackState.LOADING;
  private volatile String detail = "loading";
  private volatile String targetVersion = "";

  public PluginApiPackRuntime(PluginApiPackDefinition definition) {
    this.definition = definition;
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
    Plugin target = Bukkit.getPluginManager().getPlugin(definition.targetPlugin());
    if (target == null || !target.isEnabled()) {
      targetVersion = "";
      state = PackState.TARGET_MISSING;
      detail = "target-plugin-not-enabled";
      metrics.forEach(metric -> {
        metric.unregisterEventListener();
        metric.unavailable("target-plugin-not-enabled", 0L, false);
      });
      return;
    }
    targetVersion = target.getPluginMeta().getVersion();
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
          || metric.eventListener() != null;
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

  private void collectMetric(PluginApiMetricRuntime metric, Plugin target, long nowMs) {
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
        React.warn("Plugin API metric sampled slowly: pack=" + definition.id()
            + " metric=" + metric.definition().id() + " duration=" + durationMs + "ms");
      }
    } catch (Throwable failure) {
      long durationMs = (System.nanoTime() - started) / 1_000_000L;
      metric.unavailable(failure.getClass().getSimpleName(), durationMs, true);
      if (previousFailures == 0 || metric.quarantined()) {
        React.warn("Plugin API metric sample failed: pack=" + definition.id()
            + " metric=" + metric.definition().id()
            + " source=" + metric.definition().source().type().name().toLowerCase()
            + " failures=" + metric.consecutiveFailures(), failure);
      }
    }
  }

  private double read(SourceDefinition source, PluginApiMetricRuntime metric, Plugin target) throws Exception {
    return switch (source.type()) {
      case INTEGRATION -> readIntegration(source);
      case PLACEHOLDER -> readPlaceholder(source);
      case ORAXEN -> PluginApiOraxenReader.read(target, source.key());
      case EVENT_COUNTER -> metric.eventCount().sum();
    };
  }

  private double readIntegration(SourceDefinition source) {
    IntegrationController controller = React.controller(IntegrationController.class);
    RemoteSamplerBridge bridge = controller == null ? null : controller.getRemoteSamplerBridge();
    if (bridge == null || !bridge.isAvailable(source.pluginId(), source.key())) {
      return Double.NaN;
    }
    return bridge.valueOr(source.pluginId(), source.key(), Double.NaN);
  }

  private double readPlaceholder(SourceDefinition source) {
    if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      return Double.NaN;
    }
    if (J.isFoliaThreading() && !source.foliaSafe()) {
      return Double.NaN;
    }
    return PluginApiPlaceholderReader.read(source.placeholder());
  }

  @SuppressWarnings("unchecked")
  private void ensureEventListener(PluginApiMetricRuntime metric, Plugin target) {
    if (metric.definition().source().type() != SourceType.EVENT_COUNTER
        || metric.eventListener() != null
        || metric.quarantined()) {
      return;
    }
    try {
      ClassLoader classLoader = target.getClass().getClassLoader();
      Class<?> rawEventClass = Class.forName(metric.definition().source().eventClass(), false, classLoader);
      if (!Event.class.isAssignableFrom(rawEventClass)) {
        throw new IllegalArgumentException("Configured class is not a Bukkit Event");
      }
      Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
      Listener listener = new Listener() {
      };
      EventExecutor executor = (ignored, event) -> metric.eventCount().increment();
      Bukkit.getPluginManager().registerEvent(
          eventClass,
          listener,
          EventPriority.MONITOR,
          executor,
          React.instance,
          true
      );
      metric.eventListener(listener);
    } catch (Throwable failure) {
      metric.unavailable("event-registration-failed", 0L, true);
      if (metric.consecutiveFailures() == 1 || metric.quarantined()) {
        React.warn("Plugin API event registration failed: pack=" + definition.id()
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
