package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesPlateBuilds extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-plate-builds";

  public SamplerWormholesPlateBuilds() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PLATE_BUILDS_PER_SECOND, 1, " plates/s");
  }

  @Override
  public Material getIcon() {
    return Material.GLASS_PANE;
  }
}
