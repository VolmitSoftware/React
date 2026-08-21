package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesProjectionsActive extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-projections-active";

  public SamplerWormholesProjectionsActive() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PROJECTIONS_ACTIVE, 0, " proj");
  }

  @Override
  public Material getIcon() {
    return Material.ENDER_EYE;
  }
}
