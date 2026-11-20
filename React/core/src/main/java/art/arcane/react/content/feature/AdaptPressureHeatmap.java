package art.arcane.react.content.feature;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;

/**
 * Chunk cost weighted by Adapt's session load and ability timing budget.
 */
public class AdaptPressureHeatmap extends ChunkHeatmapRenderer {
  public static final String ID = "adapt-runtime-pressure-overlay";
  private final IntegrationMetricsHost integrations;

  public AdaptPressureHeatmap(ChunkHeatmapHost data, MapAnchorHost anchors, Settings settings, IntegrationMetricsHost integrations) {
    super(ID, data, anchors, settings);
    this.integrations = integrations;
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_ADAPT_PRESSURE);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(152, 82, 138);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(10, 8, 18);
  }

  @Override
  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    double base = chunkTotalScore(world, chunkX, chunkZ);
    double sessionLoad = metricOr(IntegrationMetricSchema.ADAPT_SESSION_LOAD);
    double abilityTimingBudget = metricOr(IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET);
    return pressureScore(base, sessionLoad, abilityTimingBudget);
  }

  public static double pressureScore(double base, double sessionLoad, double abilityTimingBudget) {
    double loadPressure = Math.max(0D, sessionLoad) * 1.3D;
    double timingPressure = Math.log10(1D + Math.max(0D, abilityTimingBudget)) * 18D;
    return (base * 0.35D) + loadPressure + timingPressure;
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    return gradient(normalized, new TinyColor(60, 50, 150), new TinyColor(255, 100, 110));
  }

  private double metricOr(String key) {
    IntegrationMetricSample sample = integrations.available() ? integrations.sample("adapt", key) : null;
    return sample == null ? 0D : sample.valueOr(0D);
  }
}
