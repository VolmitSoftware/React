package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesCompression extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-compression";

  public SamplerWormholesCompression() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_COMPRESSION_RATIO_OUT, 2, "");
  }

  @Override
  public Material getIcon() {
    return Material.PISTON;
  }
}
