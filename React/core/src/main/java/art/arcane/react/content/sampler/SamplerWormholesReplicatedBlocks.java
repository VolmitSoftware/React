package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesReplicatedBlocks extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-replicated-blocks";

  public SamplerWormholesReplicatedBlocks() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_REPLICATED_BLOCKS_PER_SECOND, 0, " blk/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:grass_block";
  }
}
