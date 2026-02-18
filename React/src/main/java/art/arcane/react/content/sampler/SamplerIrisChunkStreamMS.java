package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerIrisChunkStreamMS extends RemoteIntegrationSampler {
  public static final String ID = "iris-chunk-stream-ms";
  private static final double IDLE_SPIKE_RESET_MS = 2_000D;
  private static final double MAX_STREAM_MS = 2_500D;
  private static final double SMOOTHING_ALPHA = 0.35D;
  private static final double MAX_STEP_UP_RATIO = 0.85D;
  private static final double MIN_STEP_UP_MS = 50D;
  private transient double smoothedStreamMS;
  private transient boolean smoothingPrimed;

  public SamplerIrisChunkStreamMS() {
    super(ID, "iris", IntegrationMetricSchema.IRIS_CHUNK_STREAM_MS, 2, " ms");
  }

  public static double normalizeStreamMetric(double streamMs, double queue) {
    if (!Double.isFinite(streamMs)) {
      return 0D;
    }

    double normalized = Math.max(0D, streamMs);
    if (normalized <= 0D) {
      return 0D;
    }

    if (Double.isFinite(queue) && queue <= 0D && normalized >= IDLE_SPIKE_RESET_MS) {
      return 0D;
    }

    return Math.min(MAX_STREAM_MS, normalized);
  }

  @Override
  public double onSample() {
    double queue = readMetric(IntegrationMetricSchema.IRIS_PREGEN_QUEUE, Double.NaN);
    double normalized = normalizeStreamMetric(super.onSample(), queue);
    if (normalized <= 0D) {
      resetSmoothing();
      return 0D;
    }

    if (!smoothingPrimed || !Double.isFinite(smoothedStreamMS) || smoothedStreamMS <= 0D) {
      smoothedStreamMS = normalized;
      smoothingPrimed = true;
      return smoothedStreamMS;
    }

    double maxStepUp = Math.max(MIN_STEP_UP_MS, smoothedStreamMS * MAX_STEP_UP_RATIO);
    if (normalized > smoothedStreamMS + maxStepUp) {
      normalized = smoothedStreamMS + maxStepUp;
    }

    smoothedStreamMS = (smoothedStreamMS * (1D - SMOOTHING_ALPHA)) + (normalized * SMOOTHING_ALPHA);
    if (!Double.isFinite(smoothedStreamMS) || smoothedStreamMS < 0D) {
      resetSmoothing();
      return 0D;
    }

    return smoothedStreamMS;
  }

  @Override
  public void start() {
    super.start();
    resetSmoothing();
  }

  @Override
  public void stop() {
    resetSmoothing();
    super.stop();
  }

  @Override
  public Material getIcon() {
    return Material.OAK_SAPLING;
  }

  private double readMetric(String key, double fallback) {
    IntegrationController integration = React.controller(IntegrationController.class);
    if (integration == null || integration.getRemoteSamplerBridge() == null) {
      return fallback;
    }

    return integration.getRemoteSamplerBridge().valueOr("iris", key, fallback);
  }

  private void resetSmoothing() {
    smoothedStreamMS = 0D;
    smoothingPrimed = false;
  }
}
