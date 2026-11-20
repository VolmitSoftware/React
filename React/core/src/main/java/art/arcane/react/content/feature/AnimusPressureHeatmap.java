package art.arcane.react.content.feature;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.data.TinyColor;

import java.util.function.Supplier;

/**
 * Chunk cost weighted by Animus's peak subsystem pressure and total cost.
 */
public class AnimusPressureHeatmap extends ChunkHeatmapRenderer {
  public static final String ID = "animus-runtime-pressure-overlay";
  private final Supplier<AnimusMetrics.Snapshot> metrics;

  public AnimusPressureHeatmap(ChunkHeatmapHost data, MapAnchorHost anchors, Settings settings, Supplier<AnimusMetrics.Snapshot> metrics) {
    super(ID, data, anchors, settings);
    this.metrics = metrics;
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_ANIMUS_PRESSURE);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(58, 142, 158);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(6, 12, 16);
  }

  @Override
  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    AnimusMetrics.Snapshot snapshot = metrics.get();
    return pressureScore(chunkTotalScore(world, chunkX, chunkZ), snapshot.peakPressure(), snapshot.totalCostMs());
  }

  public static double pressureScore(double base, double peakPressurePercent, double totalCostMs) {
    double pressure = Double.isFinite(peakPressurePercent) ? Math.max(0D, Math.min(100D, peakPressurePercent)) : 0D;
    double cost = Double.isFinite(totalCostMs) ? Math.max(0D, totalCostMs) : 0D;
    return (Math.max(0D, base) * 0.35D) + (pressure * 1.3D) + (Math.log10(1D + cost) * 18D);
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    return gradient(normalized, new TinyColor(40, 92, 140), new TinyColor(255, 138, 72));
  }
}
