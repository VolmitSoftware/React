package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesTransfers extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-transfers";

  public SamplerWormholesTransfers() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_TRANSFERS_IN_FLIGHT, 0, " players");
  }

  @Override
  public Material getIcon() {
    return Material.ENDER_PEARL;
  }
}
