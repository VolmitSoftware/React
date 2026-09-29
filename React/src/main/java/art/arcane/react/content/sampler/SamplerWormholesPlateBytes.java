package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesPlateBytes extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-plate-bytes";

  public SamplerWormholesPlateBytes() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PLATE_BYTES, 0, " B");
  }

  @Override
  public Material getIcon() {
    return Material.MAP;
  }
}
