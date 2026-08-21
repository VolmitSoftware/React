package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesBlockChanges extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-block-changes";

  public SamplerWormholesBlockChanges() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_BLOCK_CHANGES_PER_SECOND, 0, " b/s");
  }

  @Override
  public Material getIcon() {
    return Material.OBSIDIAN;
  }
}
