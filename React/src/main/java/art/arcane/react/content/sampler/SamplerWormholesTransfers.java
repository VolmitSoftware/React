package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesTransfers extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-transfers";

  public SamplerWormholesTransfers() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_TRANSFERS_IN_FLIGHT, 0, " players");
  }

  @Override
  public String getIcon() {
    return "minecraft:ender_pearl";
  }
}
