package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerIrisChunksPerSecond extends RemoteIntegrationSampler {
  public static final String ID = "iris-chunks-per-second";

  public SamplerIrisChunksPerSecond() {
    super(ID, "iris", IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, 1, " ch/s");
  }

  @Override
  public Material getIcon() {
    return Material.GRASS_BLOCK;
  }
}
