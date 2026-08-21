package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesReplicatedBlocks extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-replicated-blocks";

  public SamplerWormholesReplicatedBlocks() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_REPLICATED_BLOCKS_PER_SECOND, 0, " blk/s");
  }

  @Override
  public Material getIcon() {
    return Material.GRASS_BLOCK;
  }
}
