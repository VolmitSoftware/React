package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerIrisChunksPerSecond extends RemoteIntegrationSampler {
  public static final String ID = "iris-chunks-per-second";

  public SamplerIrisChunksPerSecond() {
    super(ID, "iris", IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, 1, " ch/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:grass_block";
  }
}
