package art.arcane.react.content.feature;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.data.TinyColor;

public class ChunkCostHeatmap extends ChunkHeatmapRenderer {
  public static final String ID = "chunk-sampler-map";

  public ChunkCostHeatmap(ChunkHeatmapHost data, MapAnchorHost anchors, Settings settings) {
    super(ID, data, anchors, settings);
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_CHUNK_COST_HEAT);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(84, 114, 146);
  }

  @Override
  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    if (world == null) {
      return 0D;
    }
    return Math.max(0D, chunkTotalScore(world, chunkX, chunkZ));
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    if (normalized < 0.33D) {
      return gradient(normalized / 0.33D, new TinyColor(22, 84, 190), new TinyColor(52, 182, 214));
    }
    if (normalized < 0.66D) {
      return gradient((normalized - 0.33D) / 0.33D, new TinyColor(52, 182, 214), new TinyColor(245, 184, 74));
    }
    return gradient((normalized - 0.66D) / 0.34D, new TinyColor(245, 184, 74), new TinyColor(255, 82, 44));
  }
}
