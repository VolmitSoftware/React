package art.arcane.react.core.integration;

import art.arcane.react.React;
import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.ReactMetricSource;
import art.arcane.react.api.metric.internal.MetricInstaller;
import art.arcane.react.api.metric.internal.MetricKeys;
import art.arcane.react.api.metric.internal.MetricSourceGateway;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.SampleController;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ThirdPartyMetricRegistry {
  public static final long METRIC_REFRESH_MS = 60_000L;

  private final PublishedMetricStore store = new PublishedMetricStore();
  private final MetricSourceGateway gateway;
  private final Map<String, SourceState> states = new LinkedHashMap<>();

  public ThirdPartyMetricRegistry() {
    this.gateway = new MetricSourceGateway(
        MetricSourceGateway.DEFAULT_FAULT_LIMIT,
        MetricSourceGateway.DEFAULT_SLOW_SOURCE_MILLIS,
        React::verbose);
  }

  public PublishedMetricStore store() {
    return store;
  }

  public MetricSourceGateway gateway() {
    return gateway;
  }

  public void install() {
    MetricInstaller.install(store);
    MetricInstaller.installHostMetrics(
        ThirdPartyMetricRegistry::hostMetricKeys, ThirdPartyMetricRegistry::readHostMetric);
  }

  public void uninstall() {
    for (SourceState state : new ArrayList<>(states.values())) {
      retireSamplers(state);
    }

    states.clear();
    store.clear();
    MetricInstaller.uninstall(store);
    MetricInstaller.installHostMetrics(null, null);
  }

  public int reconcile(long now) {
    Map<String, ReactMetricSource> discovered = new LinkedHashMap<>();
    Map<String, String> owners = new LinkedHashMap<>();

    for (RegisteredServiceProvider<ReactMetricSource> registration : registrations()) {
      if (registration == null
          || registration.getProvider() == null
          || registration.getPlugin() == null
          || !registration.getPlugin().isEnabled()) {
        continue;
      }

      String pluginName = registration.getPlugin().getName();
      String sourceId = gateway.sourceIdOf(registration.getProvider(), pluginName);

      if (sourceId.isEmpty() || discovered.containsKey(sourceId)) {
        continue;
      }

      discovered.put(sourceId, registration.getProvider());
      owners.put(sourceId, pluginName);
    }

    for (String goneSourceId : new LinkedHashSet<>(states.keySet())) {
      if (!discovered.containsKey(goneSourceId)) {
        SourceState state = states.remove(goneSourceId);
        retireSamplers(state);
        store.withdrawSource(goneSourceId);
        React.verbose(() -> "[metric] source " + goneSourceId + " withdrawn");
      }
    }

    for (Map.Entry<String, ReactMetricSource> entry : discovered.entrySet()) {
      String sourceId = entry.getKey();
      SourceState state = states.get(sourceId);
      boolean fresh = state == null;

      if (!fresh && state.source == entry.getValue() && now - state.declaredAtMs < METRIC_REFRESH_MS) {
        continue;
      }

      List<ReactMetric> declared = gateway.declarationOf(entry.getValue(), sourceId, owners.get(sourceId));

      if (declared == null) {
        continue;
      }

      List<ReactMetric> accepted = store.declare(sourceId, declared);

      if (state == null) {
        state = new SourceState(sourceId);
        states.put(sourceId, state);
      }

      state.source = entry.getValue();
      state.declaredAtMs = now;
      syncSamplers(state, accepted);

      if (fresh) {
        React.verbose(() -> "[metric] source " + sourceId + " from " + owners.get(sourceId)
            + " declared " + accepted.size() + "/" + declared.size() + " metrics");
      }
    }

    return states.size();
  }

  @SuppressWarnings("unchecked")
  private Collection<RegisteredServiceProvider<ReactMetricSource>> registrations() {
    try {
      Collection<RegisteredServiceProvider<ReactMetricSource>> found =
          Bukkit.getServicesManager().getRegistrations(ReactMetricSource.class);
      return found == null ? List.of() : found;
    } catch (Throwable e) {
      React.warn("[metric] source discovery failed: " + e.getClass().getSimpleName()
          + (e.getMessage() == null ? "" : " - " + e.getMessage()), e);
      return List.of();
    }
  }

  private void syncSamplers(SourceState state, List<ReactMetric> accepted) {
    SampleController controller = React.controller(SampleController.class);

    if (controller == null) {
      return;
    }

    Set<String> wanted = new LinkedHashSet<>();

    for (ReactMetric metric : accepted) {
      String samplerId = MetricKeys.samplerIdFor(metric.key());

      if (samplerId.isEmpty()) {
        continue;
      }

      wanted.add(samplerId);

      if (state.samplerIds.contains(samplerId)) {
        Sampler current = controller.getSampler(samplerId);
        if (current instanceof PublishedMetricSampler publishedMetricSampler) {
          publishedMetricSampler.updateMetric(metric);
          continue;
        }

        state.samplerIds.remove(samplerId);
        if (current != null) {
          React.warn("[metric] sampler id " + samplerId + " for " + metric.key() + " is already taken; skipping");
          continue;
        }
      }

      Sampler existing = controller.getSampler(samplerId);

      if (existing != null) {
        React.warn("[metric] sampler id " + samplerId + " for " + metric.key() + " is already taken; skipping");
        continue;
      }

      if (controller.registerSampler(new PublishedMetricSampler(samplerId, metric, store))) {
        state.samplerIds.add(samplerId);
      }
    }

    for (String samplerId : new ArrayList<>(state.samplerIds)) {
      if (!wanted.contains(samplerId)) {
        controller.unregisterSampler(samplerId);
        state.samplerIds.remove(samplerId);
      }
    }
  }

  private void retireSamplers(SourceState state) {
    if (state == null) {
      return;
    }

    SampleController controller = React.controller(SampleController.class);

    if (controller != null) {
      for (String samplerId : state.samplerIds) {
        controller.unregisterSampler(samplerId);
      }
    }

    state.samplerIds.clear();
  }

  private static Set<String> hostMetricKeys() {
    SampleController controller = React.controller(SampleController.class);

    if (controller == null || controller.getSamplers() == null) {
      return Set.of();
    }

    return Set.copyOf(controller.getSamplers().ids());
  }

  private static double readHostMetric(String key) {
    Sampler sampler = React.sampler(key);
    return sampler == null ? Double.NaN : sampler.sample();
  }

  private static final class SourceState {
    private final String sourceId;
    private final Set<String> samplerIds = new LinkedHashSet<>();
    private ReactMetricSource source;
    private long declaredAtMs;

    private SourceState(String sourceId) {
      this.sourceId = sourceId;
    }
  }
}
