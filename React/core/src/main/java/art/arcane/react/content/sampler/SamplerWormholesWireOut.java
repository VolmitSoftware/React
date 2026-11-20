package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesWireOut extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-wire-out";

  public SamplerWormholesWireOut() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_WIRE_BYTES_OUT_PER_SECOND, 0, " B/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:dispenser";
  }
}
