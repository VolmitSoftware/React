package art.arcane.react.content.feature;

import art.arcane.react.api.rendering.MapTheme;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.Region;
import art.arcane.react.api.rendering.RendererLayout;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.data.TinyColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Where recent tick spikes came from, drawn from a {@link TickSpikeReplay}.
 */
public class TickSpikeReplayHeatmap extends ChunkHeatmapRenderer {
  public static final String ID = "tick-spike-origin-replay-map";
  private final TickSpikeReplay replay;

  public TickSpikeReplayHeatmap(ChunkHeatmapHost data, MapAnchorHost anchors, Settings settings, TickSpikeReplay replay) {
    super(ID, data, anchors, settings);
    this.replay = replay;
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_TICK_SPIKE_REPLAY);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(168, 86, 34);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(18, 9, 5);
  }

  @Override
  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    return replay.heat(world.worldId(), chunkX, chunkZ);
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    if (normalized < 0.5D) {
      return gradient(normalized * 2D, new TinyColor(70, 40, 0), new TinyColor(255, 120, 10));
    }

    return gradient((normalized - 0.5D) * 2D, new TinyColor(255, 120, 10), new TinyColor(255, 240, 180));
  }

  @Override
  protected void renderOverlay(Map<LoadedChunkCoordinate, Double> score, double min, double max) {
    MegamapGrid.MegamapDetail detail = megamapDetail();
    if (score.isEmpty() || !detail.atLeast(MegamapGrid.MegamapDetail.EXPANDED)) {
      return;
    }

    int limit = detail.atLeast(MegamapGrid.MegamapDetail.RICH) ? 8 : 3;
    List<Map.Entry<LoadedChunkCoordinate, Double>> ranked = highest(score, limit);

    int scale = uiScale();
    int marker = Math.max(2, 3 * scale);
    for (int i = 0; i < ranked.size(); i++) {
      Map.Entry<LoadedChunkCoordinate, Double> entry = ranked.get(i);
      Pixel pixel = projectChunk(entry.getKey());
      Region badge = new Region(pixel.x() - marker, pixel.y() - marker, (2 * marker) + 1, (2 * marker) + 1);
      border(badge, MapTheme.TEXT_STRONG, Math.max(1, scale));

      Region label = new Region(badge.right() + scale, pixel.y() - (MapTheme.lineHeight(scale) / 2), 40 * scale, MapTheme.lineHeight(scale));
      textIn(label, 0, 0, "#" + (i + 1) + " " + RendererLayout.compact(entry.getValue()), MapTheme.TEXT_STRONG);
    }
  }

  private static List<Map.Entry<LoadedChunkCoordinate, Double>> highest(Map<LoadedChunkCoordinate, Double> score, int limit) {
    List<Map.Entry<LoadedChunkCoordinate, Double>> ranked = new ArrayList<>(limit);
    for (Map.Entry<LoadedChunkCoordinate, Double> candidate : score.entrySet()) {
      double value = candidate.getValue() == null ? 0D : candidate.getValue();
      if (ranked.size() >= limit && value <= ranked.get(ranked.size() - 1).getValue()) {
        continue;
      }

      int slot = 0;
      while (slot < ranked.size() && ranked.get(slot).getValue() >= value) {
        slot++;
      }

      ranked.add(slot, candidate);
      if (ranked.size() > limit) {
        ranked.remove(ranked.size() - 1);
      }
    }

    return ranked;
  }
}
