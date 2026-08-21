package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesPackets extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-packets";

  public SamplerWormholesPackets() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PACKETS_PER_SECOND, 0, " pk/s");
  }

  @Override
  public Material getIcon() {
    return Material.PAPER;
  }
}
