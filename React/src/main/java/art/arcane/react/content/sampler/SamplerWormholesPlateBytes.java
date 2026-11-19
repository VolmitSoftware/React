package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesPlateBytes extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-plate-bytes";

  public SamplerWormholesPlateBytes() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PLATE_BYTES, 0, " B");
  }

  @Override
  public String getIcon() {
    return "minecraft:map";
  }
}
