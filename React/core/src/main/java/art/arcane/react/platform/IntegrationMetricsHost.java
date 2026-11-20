package art.arcane.react.platform;

import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;

import java.util.List;
import java.util.Map;

/**
 * Metrics other plugins or mods publish to React, keyed by their integration id ({@code iris}, {@code adapt}, ...).
 * Called from map render threads.
 */
public interface IntegrationMetricsHost {
  IntegrationMetricsHost NONE = new IntegrationMetricsHost() {
    @Override
    public boolean available() {
      return false;
    }

    @Override
    public Status status(String integrationId) {
      return null;
    }

    @Override
    public IntegrationMetricSample sample(String integrationId, String key) {
      return null;
    }

    @Override
    public Map<String, IntegrationMetricSample> samples(String integrationId) {
      return Map.of();
    }

    @Override
    public IntegrationMetricGroup group(String integrationId, String scopeKind, String scopeId) {
      return null;
    }

    @Override
    public List<IntegrationMetricGroup> groups(String integrationId, String scopeKind) {
      return List.of();
    }

    @Override
    public String worldScope(MapRenderTarget target) {
      return null;
    }
  };

  /**
   * False while nothing collects integration metrics at all.
   */
  boolean available();

  /**
   * Null when the integration was never seen.
   */
  Status status(String integrationId);

  IntegrationMetricSample sample(String integrationId, String key);

  /**
   * Every sample the integration published, keyed by metric key.
   */
  Map<String, IntegrationMetricSample> samples(String integrationId);

  IntegrationMetricGroup group(String integrationId, String scopeKind, String scopeId);

  /**
   * Every group of one scope kind, ordered by scope id.
   */
  List<IntegrationMetricGroup> groups(String integrationId, String scopeKind);

  /**
   * The world scope id integrations publish per-world groups under, for the world the map shows; null when none.
   */
  String worldScope(MapRenderTarget target);

  /**
   * {@code health} is HEALTHY, DEGRADED, STALE or MISSING.
   */
  record Status(String health, String message) {
  }
}
