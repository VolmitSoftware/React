package art.arcane.react.content.feature;

import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;

import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Animus Runtime Pressure Overlay feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureAnimusRuntimePressureOverlay extends FeatureChunkHeatmapBase implements CapabilityGatedFeature {
  public static final String ID = AnimusPressureHeatmap.ID;

  public FeatureAnimusRuntimePressureOverlay() {
    super(ID);
  }

  @Override
  protected ChunkHeatmapRenderer createRenderer(ChunkHeatmapHost data, MapAnchorHost anchors) {
    return new AnimusPressureHeatmap(data, anchors, this, AnimusMetricSeries::snapshot);
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of(AnimusMetrics.SOURCE_ID);
  }
}
