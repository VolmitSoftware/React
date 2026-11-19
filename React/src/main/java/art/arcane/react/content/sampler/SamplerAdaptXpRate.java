package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerAdaptXpRate extends RemoteIntegrationSampler {
  public static final String ID = "adapt-xp-rate";

  public SamplerAdaptXpRate() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_XP_PER_MINUTE, 0, " xp/m");
  }

  @Override
  public String getIcon() {
    return "minecraft:experience_bottle";
  }
}
