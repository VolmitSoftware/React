package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Chunk;

public class FeatureIrisGenerationPressureOverlay extends FeatureChunkHeatmapBase {
    public static final String ID = "iris-generation-pressure-overlay";

    public FeatureIrisGenerationPressureOverlay() {
        super(ID);
    }

    @Override
    protected String mapLabel() {
        return "Iris Pressure";
    }

    @Override
    protected TinyColor backgroundColor() {
        return new TinyColor(6, 14, 12);
    }

    @Override
    protected double chunkScore(Chunk chunk) {
        double base = chunkTotalScore(chunk);
        double queue = metricOr(IntegrationMetricSchema.IRIS_PREGEN_QUEUE, 0D);
        double streamMs = metricOr(IntegrationMetricSchema.IRIS_CHUNK_STREAM_MS, 0D);

        double queuePressure = Math.log10(1D + Math.max(0D, queue)) * 14D;
        double streamPressure = Math.min(120D, Math.max(0D, streamMs) * 1.5D);
        return (base * 0.40D) + queuePressure + streamPressure;
    }

    @Override
    protected TinyColor colorFor(double normalized, double rawScore) {
        return gradient(normalized, new TinyColor(40, 110, 80), new TinyColor(255, 180, 60));
    }

    private double metricOr(String key, double fallback) {
        IntegrationController controller = React.controller(IntegrationController.class);
        if (controller == null || controller.getRemoteSamplerBridge() == null) {
            return fallback;
        }

        return controller.getRemoteSamplerBridge().valueOr("iris", key, fallback);
    }
}
