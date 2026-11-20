package art.arcane.react.core.integration;

import art.arcane.react.api.metric.ReactModMetric;
import art.arcane.react.api.metric.ReactModMetricSource;
import art.arcane.react.api.metric.internal.HostMetrics;
import art.arcane.react.api.metric.internal.MetricInstaller;
import art.arcane.react.api.metric.internal.MetricKeys;
import art.arcane.react.api.metric.internal.MetricSourceGateway;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.PlatformLog;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class ThirdPartyMetricRegistry {
  public static final long METRIC_REFRESH_MS = 60_000L;

  private final PublishedMetricStore store = new PublishedMetricStore();
  private final MetricSourceGateway gateway;
  private final Map<String, SourceState> states = new LinkedHashMap<>();
  private final Supplier<List<Registration>> registrations;

  /**
   * {@code registrations} lists every metric source the platform knows with its owning plugin or mod; it runs on the
   * server thread.
   */
  public ThirdPartyMetricRegistry(Supplier<List<Registration>> registrations) {
    this.registrations = registrations;
    this.gateway = new MetricSourceGateway(
        MetricSourceGateway.DEFAULT_FAULT_LIMIT,
        MetricSourceGateway.DEFAULT_SLOW_SOURCE_MILLIS,
        ThirdPartyMetricRegistry::verbose);
  }

  public PublishedMetricStore store() {
    return store;
  }

  public MetricSourceGateway gateway() {
    return gateway;
  }

  public void install() {
    MetricInstaller.install(store);
    HostMetrics.install();
  }

  public void uninstall() {
    for (SourceState state : new ArrayList<>(states.values())) {
      retireSamplers(state);
    }

    states.clear();
    store.clear();
    MetricInstaller.uninstall(store);
    HostMetrics.uninstall();
  }

  public int reconcile(long now) {
    Map<String, ReactModMetricSource> discovered = new LinkedHashMap<>();
    Map<String, String> owners = new LinkedHashMap<>();

    for (Registration registration : registrations()) {
      if (registration == null || registration.source() == null || registration.owner() == null) {
        continue;
      }

      String owner = registration.owner();
      String sourceId = gateway.sourceIdOf(registration.source(), owner);

      if (sourceId.isEmpty() || discovered.containsKey(sourceId)) {
        continue;
      }

      discovered.put(sourceId, registration.source());
      owners.put(sourceId, owner);
    }

    for (String goneSourceId : new LinkedHashSet<>(states.keySet())) {
      if (!discovered.containsKey(goneSourceId)) {
        SourceState state = states.remove(goneSourceId);
        retireSamplers(state);
        store.withdrawSource(goneSourceId);
        verbose(() -> "[metric] source " + goneSourceId + " withdrawn");
      }
    }

    for (Map.Entry<String, ReactModMetricSource> entry : discovered.entrySet()) {
      String sourceId = entry.getKey();
      SourceState state = states.get(sourceId);
      boolean fresh = state == null;

      if (!fresh && state.source.equals(entry.getValue()) && now - state.declaredAtMs < METRIC_REFRESH_MS) {
        continue;
      }

      List<ReactModMetric> declared = gateway.declarationOf(entry.getValue(), sourceId, owners.get(sourceId));

      if (declared == null) {
        continue;
      }

      List<ReactModMetric> accepted = store.declare(sourceId, declared);

      if (state == null) {
        state = new SourceState(sourceId);
        states.put(sourceId, state);
      }

      state.source = entry.getValue();
      state.declaredAtMs = now;
      syncSamplers(state, accepted);

      if (fresh) {
        verbose(() -> "[metric] source " + sourceId + " from " + owners.get(sourceId)
            + " declared " + accepted.size() + "/" + declared.size() + " metrics");
      }
    }

    return states.size();
  }

  private List<Registration> registrations() {
    try {
      List<Registration> found = registrations.get();
      return found == null ? List.of() : found;
    } catch (Throwable e) {
      PlatformLog.warn("[metric] source discovery failed: " + e.getClass().getSimpleName()
          + (e.getMessage() == null ? "" : " - " + e.getMessage()), e);
      return List.of();
    }
  }

  private void syncSamplers(SourceState state, List<ReactModMetric> accepted) {
    SampleController controller = SamplerHosts.sampleController();

    if (controller == null) {
      return;
    }

    Set<String> wanted = new LinkedHashSet<>();

    for (ReactModMetric metric : accepted) {
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
          PlatformLog.warn("[metric] sampler id " + samplerId + " for " + metric.key() + " is already taken; skipping");
          continue;
        }
      }

      Sampler existing = controller.getSampler(samplerId);

      if (existing != null) {
        PlatformLog.warn("[metric] sampler id " + samplerId + " for " + metric.key() + " is already taken; skipping");
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

    SampleController controller = SamplerHosts.sampleController();

    if (controller != null) {
      for (String samplerId : state.samplerIds) {
        controller.unregisterSampler(samplerId);
      }
    }

    state.samplerIds.clear();
  }

  private static void verbose(String message) {
    verbose(() -> message);
  }

  private static void verbose(Supplier<String> message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.verbose(message);
    }
  }

  public record Registration(ReactModMetricSource source, String owner) {
  }

  private static final class SourceState {
    private final String sourceId;
    private final Set<String> samplerIds = new LinkedHashSet<>();
    private ReactModMetricSource source;
    private long declaredAtMs;

    private SourceState(String sourceId) {
      this.sourceId = sourceId;
    }
  }
}
