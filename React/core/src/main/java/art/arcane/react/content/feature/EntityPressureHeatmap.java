package art.arcane.react.content.feature;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.data.TinyColor;

public class EntityPressureHeatmap extends ChunkHeatmapRenderer {
  public static final String ID = "entity-pressure-heatmap";

  public EntityPressureHeatmap(ChunkHeatmapHost data, MapAnchorHost anchors, Settings settings) {
    super(ID, data, anchors, settings);
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_ENTITY_PRESSURE);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(150, 104, 46);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(5, 7, 14);
  }
  @Override
  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    return chunkSample(world, chunkX, chunkZ, SamplerEntities.ID);
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    if (normalized < 0.5D) {
      return gradient(normalized * 2D, new TinyColor(32, 110, 255), new TinyColor(80, 255, 120));
    }
    return gradient((normalized - 0.5D) * 2D, new TinyColor(80, 255, 120), new TinyColor(255, 80, 30));
  }
}
