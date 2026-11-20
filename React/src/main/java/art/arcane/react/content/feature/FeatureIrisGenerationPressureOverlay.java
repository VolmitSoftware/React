package art.arcane.react.content.feature;

import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.platform.bukkit.BukkitIntegrationMetricsHost;

import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Iris Generation Pressure Overlay feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIrisGenerationPressureOverlay extends FeatureChunkHeatmapBase implements CapabilityGatedFeature {
  public static final String ID = IrisPressureHeatmap.ID;

  public FeatureIrisGenerationPressureOverlay() {
    super(ID);
  }

  @Override
  protected ChunkHeatmapRenderer createRenderer(ChunkHeatmapHost data, MapAnchorHost anchors) {
    return new IrisPressureHeatmap(data, anchors, this, BukkitIntegrationMetricsHost.INSTANCE);
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("iris");
  }
}
