package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesProjectionRenderMs extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-projection-render-ms";

  public SamplerWormholesProjectionRenderMs() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PROJECTION_RENDER_MS, 2, " ms/s");
  }

  @Override
  public Material getIcon() {
    return Material.CLOCK;
  }
}
