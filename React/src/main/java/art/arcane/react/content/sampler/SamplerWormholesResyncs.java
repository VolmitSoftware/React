package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesResyncs extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-resyncs";

  public SamplerWormholesResyncs() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_RESYNC_REQUESTS_TOTAL, 0, " resyncs");
  }

  @Override
  public Material getIcon() {
    return Material.COMPASS;
  }
}
