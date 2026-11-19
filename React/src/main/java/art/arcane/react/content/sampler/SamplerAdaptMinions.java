package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerAdaptMinions extends RemoteIntegrationSampler {
  public static final String ID = "adapt-minions";

  public SamplerAdaptMinions() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_MINIONS_ACTIVE, 0, " minions");
  }

  @Override
  public String getIcon() {
    return "minecraft:zombie_head";
  }
}
