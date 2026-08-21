package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesProjectionObservers extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-projection-observers";

  public SamplerWormholesProjectionObservers() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PROJECTION_OBSERVERS, 0, " obs");
  }

  @Override
  public Material getIcon() {
    return Material.SPYGLASS;
  }
}
