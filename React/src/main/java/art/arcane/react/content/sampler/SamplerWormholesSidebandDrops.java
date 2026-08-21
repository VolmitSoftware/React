package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesSidebandDrops extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-sideband-drops";

  public SamplerWormholesSidebandDrops() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_SIDEBAND_DROPS_PER_SECOND, 1, "/s");
  }

  @Override
  public Material getIcon() {
    return Material.LAVA_BUCKET;
  }
}
