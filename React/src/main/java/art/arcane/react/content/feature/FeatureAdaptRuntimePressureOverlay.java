package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Chunk;

import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Adapt Runtime Pressure Overlay feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureAdaptRuntimePressureOverlay extends FeatureChunkHeatmapBase implements CapabilityGatedFeature {
  public static final String ID = "adapt-runtime-pressure-overlay";

  public FeatureAdaptRuntimePressureOverlay() {
    super(ID);
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
  protected double chunkScore(Chunk chunk) {
    double base = chunkTotalScore(chunk);
    double sessionLoad = metricOr(IntegrationMetricSchema.ADAPT_SESSION_LOAD, 0D);
    double abilityTimingBudget = metricOr(IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET, 0D);
    return pressureScore(base, sessionLoad, abilityTimingBudget);
  }

  static double pressureScore(double base, double sessionLoad, double abilityTimingBudget) {
    double loadPressure = Math.max(0D, sessionLoad) * 1.3D;
    double timingPressure = Math.log10(1D + Math.max(0D, abilityTimingBudget)) * 18D;
    return (base * 0.35D) + loadPressure + timingPressure;
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    return gradient(normalized, new TinyColor(60, 50, 150), new TinyColor(255, 100, 110));
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("adapt");
  }

  private double metricOr(String key, double fallback) {
    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null || controller.getRemoteSamplerBridge() == null) {
      return fallback;
    }

    return controller.getRemoteSamplerBridge().valueOr("adapt", key, fallback);
  }
}
