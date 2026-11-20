package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesCompression extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-compression";

  public SamplerWormholesCompression() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_COMPRESSION_RATIO_OUT, 2, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:piston";
  }
}
