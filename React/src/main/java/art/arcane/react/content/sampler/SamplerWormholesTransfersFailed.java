package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesTransfersFailed extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-transfers-failed";

  public SamplerWormholesTransfersFailed() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_TRANSFERS_FAILED_TOTAL, 0, " failed");
  }

  @Override
  public Material getIcon() {
    return Material.BARRIER;
  }
}
