package art.arcane.react.content.feature;

import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;

import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Animus Runtime Pressure Overlay feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureAnimusRuntimePressureOverlay extends FeatureChunkHeatmapBase implements CapabilityGatedFeature {
  public static final String ID = "animus-runtime-pressure-overlay";

  public FeatureAnimusRuntimePressureOverlay() {
    super(ID);
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
    AnimusMetricSeries.Snapshot snapshot = AnimusMetricSeries.snapshot();
    return pressureScore(chunkTotalScore(world, chunkX, chunkZ), snapshot.peakPressure(), snapshot.totalCostMs());
  }

  static double pressureScore(double base, double peakPressurePercent, double totalCostMs) {
    double pressure = Double.isFinite(peakPressurePercent) ? Math.max(0D, Math.min(100D, peakPressurePercent)) : 0D;
    double cost = Double.isFinite(totalCostMs) ? Math.max(0D, totalCostMs) : 0D;
    return (Math.max(0D, base) * 0.35D) + (pressure * 1.3D) + (Math.log10(1D + cost) * 18D);
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    return gradient(normalized, new TinyColor(40, 92, 140), new TinyColor(255, 138, 72));
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of(AnimusMetricSeries.SOURCE_ID);
  }
}
