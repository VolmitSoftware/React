package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.ThirdPartyMetricRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Animus metrics from the values it publishes through React's metric API.
 */
final class AnimusMetricSeries {
  private static final long CACHE_TTL_MS = 250L;
  private static volatile CachedSnapshot cached = new CachedSnapshot(AnimusMetrics.Snapshot.empty(), 0L);

  private AnimusMetricSeries() {
  }

  static AnimusMetrics.Snapshot snapshot() {
    long now = System.currentTimeMillis();
    CachedSnapshot current = cached;
    if (now >= current.capturedAtMs() && now - current.capturedAtMs() < CACHE_TTL_MS) {
      return current.snapshot();
    }

    IntegrationController controller = React.controller(IntegrationController.class);
    ThirdPartyMetricRegistry registry = controller == null ? null : controller.getThirdPartyMetrics();
    AnimusMetrics.Snapshot snapshot = fromStore(registry == null ? null : registry.store(), now);
    cached = new CachedSnapshot(snapshot, now);
    return snapshot;
  }

  static AnimusMetrics.Snapshot fromStore(PublishedMetricStore store, long now) {
    if (store == null) {
      return AnimusMetrics.Snapshot.empty();
    }

    List<ReactMetric> declared = store.declared(AnimusMetrics.SOURCE_ID);
    Map<String, Double> readings = new LinkedHashMap<>();
    for (ReactMetric metric : declared) {
      PublishedMetricStore.Reading reading = store.reading(metric.key(), now);
      if (reading != null) {
        readings.put(metric.key(), reading.value());
      }
    }
    return AnimusMetrics.parse(!declared.isEmpty(), readings);
  }

  private record CachedSnapshot(AnimusMetrics.Snapshot snapshot, long capturedAtMs) {
  }
}
