package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesTraversals extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-traversals";

  public SamplerWormholesTraversals() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_TRAVERSALS_PER_MINUTE, 1, " /min");
  }

  @Override
  public Material getIcon() {
    return Material.ENDER_PEARL;
  }
}
