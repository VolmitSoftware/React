package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.content.sampler.SamplerIrisChunkStreamMS;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Chunk;

import java.util.Set;

@art.arcane.react.util.config.ConfigDescription("Configuration for Iris Generation Pressure Overlay feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIrisGenerationPressureOverlay extends FeatureChunkHeatmapBase implements CapabilityGatedFeature {
  public static final String ID = "iris-generation-pressure-overlay";

  public FeatureIrisGenerationPressureOverlay() {
    super(ID);
  }

  @Override
  protected String mapLabel() {
    return "Iris Pressure";
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(62, 136, 96);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(6, 14, 12);
  }

  @Override
  protected double chunkScore(Chunk chunk) {
    double base = chunkTotalScore(chunk);
    double queue = metricOr(IntegrationMetricSchema.IRIS_PREGEN_QUEUE, 0D);
    double streamMs = SamplerIrisChunkStreamMS.normalizeStreamMetric(
        metricOr(IntegrationMetricSchema.IRIS_CHUNK_STREAM_MS, 0D),
        queue
    );

    double queuePressure = Math.log10(1D + Math.max(0D, queue)) * 14D;
    double streamPressure = Math.min(120D, Math.max(0D, streamMs) * 1.5D);
    return (base * 0.40D) + queuePressure + streamPressure;
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    return gradient(normalized, new TinyColor(40, 110, 80), new TinyColor(255, 180, 60));
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("iris");
  }

  private double metricOr(String key, double fallback) {
    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null || controller.getRemoteSamplerBridge() == null) {
      return fallback;
    }

    return controller.getRemoteSamplerBridge().valueOr("iris", key, fallback);
  }
}
