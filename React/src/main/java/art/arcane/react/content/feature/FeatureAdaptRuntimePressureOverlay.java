package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Chunk;

public class FeatureAdaptRuntimePressureOverlay extends FeatureChunkHeatmapBase {
    public static final String ID = "adapt-runtime-pressure-overlay";

    public FeatureAdaptRuntimePressureOverlay() {
        super(ID);
    }

    @Override
    protected String mapLabel() {
        return "Adapt Pressure";
    }

    @Override
    protected TinyColor backgroundColor() {
        return new TinyColor(10, 8, 18);
    }

    @Override
    protected double chunkScore(Chunk chunk) {
        double base = chunkTotalScore(chunk);
        double sessionLoad = metricOr(IntegrationMetricSchema.ADAPT_SESSION_LOAD, 0D);
        double abilityOps = metricOr(IntegrationMetricSchema.ADAPT_ABILITY_OPS, 0D);

        double loadPressure = Math.max(0D, sessionLoad) * 1.3D;
        double opsPressure = Math.log10(1D + Math.max(0D, abilityOps)) * 18D;
        return (base * 0.35D) + loadPressure + opsPressure;
    }

    @Override
    protected TinyColor colorFor(double normalized, double rawScore) {
        return gradient(normalized, new TinyColor(60, 50, 150), new TinyColor(255, 100, 110));
    }

    private double metricOr(String key, double fallback) {
        IntegrationController controller = React.controller(IntegrationController.class);
        if (controller == null || controller.getRemoteSamplerBridge() == null) {
            return fallback;
        }

        return controller.getRemoteSamplerBridge().valueOr("adapt", key, fallback);
    }
}
