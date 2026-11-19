package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesSidebandQueue extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-sideband-queue";

  public SamplerWormholesSidebandQueue() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_SIDEBAND_QUEUED_BYTES, 0, " B");
  }

  @Override
  public String getIcon() {
    return "minecraft:chest";
  }
}
