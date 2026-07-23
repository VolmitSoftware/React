package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesPeers extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-peers";

  public SamplerWormholesPeers() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PEERS_CONNECTED, 0, " servers");
  }

  @Override
  public Material getIcon() {
    return Material.BEACON;
  }
}
