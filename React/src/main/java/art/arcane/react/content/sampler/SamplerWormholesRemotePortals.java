package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesRemotePortals extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-remote-portals";

  public SamplerWormholesRemotePortals() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_REMOTE_PORTALS, 0, " portals");
  }

  @Override
  public Material getIcon() {
    return Material.END_PORTAL;
  }
}
