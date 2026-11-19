package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossIndicators extends RemoteIntegrationSampler {
  public static final String ID = "gloss-indicators";

  public SamplerGlossIndicators() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_INDICATORS_PER_SECOND, 1, "/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:iron_sword";
  }
}
