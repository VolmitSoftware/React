package art.arcane.react.content.feature;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.data.TinyColor;

/**
 * Chunk cost, limited to worlds Iris generates.
 */
public class IrisPressureHeatmap extends ChunkHeatmapRenderer {
  public static final String ID = "iris-generation-pressure-overlay";
  private final IntegrationMetricsHost integrations;

  public IrisPressureHeatmap(ChunkHeatmapHost data, MapAnchorHost anchors, Settings settings, IntegrationMetricsHost integrations) {
    super(ID, data, anchors, settings);
    this.integrations = integrations;
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_IRIS_PRESSURE);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(62, 136, 96);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(6, 14, 12);
  }

  @Override
  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    if (world == null || !isManagedWorld(world)) {
      return 0D;
    }
    return chunkTotalScore(world, chunkX, chunkZ);
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    return gradient(normalized, new TinyColor(40, 110, 80), new TinyColor(255, 180, 60));
  }

  private boolean isManagedWorld(HeatmapWorldRef world) {
    return integrations.available() && integrations.group("iris", "world", world.worldKey()) != null;
  }
}
