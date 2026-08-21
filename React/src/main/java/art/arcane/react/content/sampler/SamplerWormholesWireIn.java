package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesWireIn extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-wire-in";

  public SamplerWormholesWireIn() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_WIRE_BYTES_IN_PER_SECOND, 0, " B/s");
  }

  @Override
  public Material getIcon() {
    return Material.HOPPER;
  }
}
