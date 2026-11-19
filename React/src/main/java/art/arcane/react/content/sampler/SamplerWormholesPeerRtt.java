package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesPeerRtt extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-peer-rtt";

  public SamplerWormholesPeerRtt() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PEER_RTT_MAX_MS, 0, " ms");
  }

  @Override
  public String getIcon() {
    return "minecraft:clock";
  }
}
