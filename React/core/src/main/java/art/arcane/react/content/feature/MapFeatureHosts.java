package art.arcane.react.content.feature;

import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.platform.MapDataHost;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The platform hosts the feature maps draw from, looked up when a renderer is first needed.
 */
final class MapFeatureHosts {
  private MapFeatureHosts() {
  }

  static ChunkHeatmapHost heatmaps() {
    return platform().flatMap(ReactPlatform::chunkHeatmapHost)
        .orElseThrow(() -> new IllegalStateException("No chunk heatmap host on this platform"));
  }

  static MapAnchorHost anchors() {
    return platform().flatMap(ReactPlatform::mapAnchorHost).orElse(MapAnchorHost.NONE);
  }

  static IntegrationMetricsHost integrations() {
    return platform().flatMap(ReactPlatform::integrationMetricsHost).orElse(IntegrationMetricsHost.NONE);
  }

  static Optional<MapDataHost> data() {
    return platform().flatMap(ReactPlatform::mapDataHost);
  }

  static boolean viewerOnline(UUID viewerId) {
    return viewerId != null && data().map((MapDataHost host) -> host.isViewerOnline(viewerId)).orElse(false);
  }

  static AnimusMetrics.Snapshot animus() {
    return data().map(MapDataHost::animusSnapshot).orElse(AnimusMetrics.Snapshot.empty());
  }

  static EventMeasurement events() {
    return data().map(MapDataHost::eventMeasurement).orElse(null);
  }

  static Map<String, Long> irisBiomes(MapRenderTarget target) {
    return data().map((MapDataHost host) -> host.irisBiomeBuckets(target)).orElse(SharePieRenderer.newCounterMap());
  }

  private static Optional<ReactPlatform> platform() {
    return Optional.ofNullable(ReactPlatforms.getOrNull());
  }
}
