package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerAdaptWorldPolicyLatency extends RemoteIntegrationSampler {
  public static final String ID = "adapt-world-policy-latency";

  public SamplerAdaptWorldPolicyLatency() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_WORLD_POLICY_LATENCY, 2, " ms");
  }

  @Override
  public String getIcon() {
    return "minecraft:bookshelf";
  }
}
