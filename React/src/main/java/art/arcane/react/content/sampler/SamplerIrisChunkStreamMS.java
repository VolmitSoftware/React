package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerIrisChunkStreamMS extends RemoteIntegrationSampler {
    public static final String ID = "iris-chunk-stream-ms";

    public SamplerIrisChunkStreamMS() {
        super(ID, "iris", IntegrationMetricSchema.IRIS_CHUNK_STREAM_MS, 2, " ms");
    }

    @Override
    public Material getIcon() {
        return Material.OAK_SAPLING;
    }
}
