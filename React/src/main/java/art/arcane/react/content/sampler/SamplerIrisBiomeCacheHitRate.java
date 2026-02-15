package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

public class SamplerIrisBiomeCacheHitRate extends RemoteIntegrationSampler {
    public static final String ID = "iris-biome-cache-hit-rate";

    public SamplerIrisBiomeCacheHitRate() {
        super(ID, "iris", IntegrationMetricSchema.IRIS_BIOME_CACHE_HIT_RATE, 1, "%");
    }

    @Override
    public Material getIcon() {
        return Material.OAK_SAPLING;
    }

    @Override
    public String formattedValue(double t) {
        if (!isAvailable()) {
            return "---";
        }

        return Form.f(t * 100D, 1);
    }
}
