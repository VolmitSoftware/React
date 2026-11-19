package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesBlockEntities extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-block-entities";

  public SamplerWormholesBlockEntities() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_BLOCK_ENTITIES_PER_SECOND, 0, " be/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:barrel";
  }
}
