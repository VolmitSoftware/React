package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerIrisPregenThroughput extends RemoteIntegrationSampler {
  public static final String ID = "iris-pregen-throughput";

  public SamplerIrisPregenThroughput() {
    super(ID, "iris", IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT, 1, " ch/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:oak_sapling";
  }
}
