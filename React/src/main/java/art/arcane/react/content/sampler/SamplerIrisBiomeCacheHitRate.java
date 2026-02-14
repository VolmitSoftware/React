package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerIrisBiomeCacheHitRate extends RemoteIntegrationSampler {
    public static final String ID = "iris-biome-cache-hit-rate";

    public SamplerIrisBiomeCacheHitRate() {
        super(ID, "iris", IntegrationMetricSchema.IRIS_BIOME_CACHE_HIT_RATE, 3, " ratio");
    }

    @Override
    public Material getIcon() {
        return Material.GRASS_BLOCK;
    }
}
