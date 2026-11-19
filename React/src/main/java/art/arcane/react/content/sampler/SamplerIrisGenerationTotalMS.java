package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerIrisGenerationTotalMS extends RemoteIntegrationSampler {
  public static final String ID = "iris-generation-total-ms";

  public SamplerIrisGenerationTotalMS() {
    super(ID, "iris", IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS, 2, " ms");
  }

  @Override
  public String getIcon() {
    return "minecraft:clock";
  }
}
