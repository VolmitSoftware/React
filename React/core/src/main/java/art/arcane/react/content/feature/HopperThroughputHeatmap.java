package art.arcane.react.content.feature;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.data.TinyColor;

import java.util.concurrent.atomic.AtomicLong;

public class HopperThroughputHeatmap extends ChunkHeatmapRenderer {
  public static final String ID = "hopper-container-throughput-map";
  public static final AtomicLong SUCK_IN_ITEMS_INVOCATIONS = new AtomicLong();

  public HopperThroughputHeatmap(ChunkHeatmapHost data, MapAnchorHost anchors, Settings settings) {
    super(ID, data, anchors, settings);
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_HOPPER_THROUGHPUT);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(46, 132, 140);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(4, 12, 14);
  }
  @Override
  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    return chunkSample(world, chunkX, chunkZ, SamplerHopperUpdates.ID);
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    if (normalized < 0.5D) {
      return gradient(normalized * 2D, new TinyColor(0, 90, 120), new TinyColor(40, 200, 220));
    }
    return gradient((normalized - 0.5D) * 2D, new TinyColor(40, 200, 220), new TinyColor(255, 200, 40));
  }
}
