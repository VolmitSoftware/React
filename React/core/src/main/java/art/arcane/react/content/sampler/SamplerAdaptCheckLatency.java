package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerAdaptCheckLatency extends RemoteIntegrationSampler {
  public static final String ID = "adapt-check-latency";

  public SamplerAdaptCheckLatency() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_ABILITY_CHECK_LATENCY_US, 1, " us");
  }

  @Override
  public String getIcon() {
    return "minecraft:redstone_torch";
  }
}
