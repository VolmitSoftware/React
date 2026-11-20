package art.arcane.react.content.feature;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.content.sampler.SamplerChunkGenListenerMS;
import art.arcane.react.content.sampler.SamplerChunkLoadListenerMS;
import art.arcane.react.content.sampler.SamplerChunksGenerated;
import art.arcane.react.content.sampler.SamplerChunksLoaded;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.data.TinyColor;

public class ChunkLoadGenCostHeatmap extends ChunkHeatmapRenderer {
  public static final String ID = "chunk-load-gen-cost-map";

  public ChunkLoadGenCostHeatmap(ChunkHeatmapHost data, MapAnchorHost anchors, Settings settings) {
    super(ID, data, anchors, settings);
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_CHUNK_LOAD_GEN_COST);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(118, 84, 176);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(10, 10, 16);
  }
  @Override
  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    double loadMS = chunkSample(world, chunkX, chunkZ, SamplerChunkLoadListenerMS.ID);
    double genMS = chunkSample(world, chunkX, chunkZ, SamplerChunkGenListenerMS.ID);
    double loadRate = chunkSample(world, chunkX, chunkZ, SamplerChunksLoaded.ID);
    double genRate = chunkSample(world, chunkX, chunkZ, SamplerChunksGenerated.ID);
    // Generation spikes are usually more expensive than plain chunk loads.
    return (loadMS * 1.00D) + (genMS * 1.35D) + (loadRate * 0.4D) + (genRate * 0.7D);
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    if (normalized < 0.5D) {
      return gradient(normalized * 2D, new TinyColor(40, 60, 180), new TinyColor(80, 180, 255));
    }
    return gradient((normalized - 0.5D) * 2D, new TinyColor(80, 180, 255), new TinyColor(255, 90, 20));
  }
}
