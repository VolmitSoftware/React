package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesPlateBuilds extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-plate-builds";

  public SamplerWormholesPlateBuilds() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_PLATE_BUILDS_PER_SECOND, 1, " plates/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:glass_pane";
  }
}
