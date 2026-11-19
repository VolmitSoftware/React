package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesProjectionsActive extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-projections-active";

  public SamplerWormholesProjectionsActive() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PROJECTIONS_ACTIVE, 0, " proj");
  }

  @Override
  public String getIcon() {
    return "minecraft:ender_eye";
  }
}
