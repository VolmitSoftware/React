package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerIrisPregenQueue extends RemoteIntegrationSampler {
  public static final String ID = "iris-pregen-queue";

  public SamplerIrisPregenQueue() {
    super(ID, "iris", IntegrationMetricSchema.IRIS_PREGEN_QUEUE, 0, " chunks");
  }

  @Override
  public String getIcon() {
    return "minecraft:oak_sapling";
  }
}
