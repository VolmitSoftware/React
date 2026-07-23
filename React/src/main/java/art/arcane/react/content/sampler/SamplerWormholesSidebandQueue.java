package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesSidebandQueue extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-sideband-queue";

  public SamplerWormholesSidebandQueue() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_SIDEBAND_QUEUED_BYTES, 0, " B");
  }

  @Override
  public Material getIcon() {
    return Material.CHEST;
  }
}
