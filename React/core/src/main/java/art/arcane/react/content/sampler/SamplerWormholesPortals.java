package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesPortals extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-portals";

  public SamplerWormholesPortals() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PORTALS, 0, " portals");
  }

  @Override
  public String getIcon() {
    return "minecraft:end_portal_frame";
  }
}
