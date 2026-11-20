package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerAdaptXpPayouts extends RemoteIntegrationSampler {
  public static final String ID = "adapt-xp-payouts";

  public SamplerAdaptXpPayouts() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_XP_PAYOUT_OPS, 0, "/m");
  }

  @Override
  public String getIcon() {
    return "minecraft:gold_nugget";
  }
}
