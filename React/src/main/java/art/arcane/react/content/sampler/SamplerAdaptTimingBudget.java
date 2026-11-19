package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerAdaptTimingBudget extends RemoteIntegrationSampler {
  public static final String ID = "adapt-timing-budget";

  public SamplerAdaptTimingBudget() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET, 1, "%");
  }

  @Override
  public String getIcon() {
    return "minecraft:comparator";
  }
}
