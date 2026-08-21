package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerWormholesViewSubscriptions extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-view-subscriptions";

  public SamplerWormholesViewSubscriptions() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_VIEW_SUBSCRIPTIONS, 0, " views");
  }

  @Override
  public Material getIcon() {
    return Material.SPYGLASS;
  }
}
