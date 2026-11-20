package art.arcane.react.content.feature;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.content.sampler.SamplerRedstoneUpdates;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.data.TinyColor;

public class RedstoneActivityHeatmap extends ChunkHeatmapRenderer {
  public static final String ID = "redstone-activity-heatmap";

  public RedstoneActivityHeatmap(ChunkHeatmapHost data, MapAnchorHost anchors, Settings settings) {
    super(ID, data, anchors, settings);
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_REDSTONE_ACTIVITY);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(162, 46, 38);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(14, 4, 4);
  }
  @Override
  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    return chunkSample(world, chunkX, chunkZ, SamplerRedstoneUpdates.ID);
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    if (normalized < 0.5D) {
      return gradient(normalized * 2D, new TinyColor(40, 0, 0), new TinyColor(180, 20, 0));
    }
    return gradient((normalized - 0.5D) * 2D, new TinyColor(180, 20, 0), new TinyColor(255, 220, 40));
  }
}
