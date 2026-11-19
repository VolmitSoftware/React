package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesRemotePortals extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-remote-portals";

  public SamplerWormholesRemotePortals() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_REMOTE_PORTALS, 0, " portals");
  }

  @Override
  public String getIcon() {
    return "minecraft:end_portal";
  }
}
