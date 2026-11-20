package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesTraversals extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-traversals";

  public SamplerWormholesTraversals() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_TRAVERSALS_PER_MINUTE, 1, " /min");
  }

  @Override
  public String getIcon() {
    return "minecraft:ender_pearl";
  }
}
