package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesViewEntities extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-view-entities";

  public SamplerWormholesViewEntities() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_VIEW_TRACKED_ENTITIES, 0, " entities");
  }

  @Override
  public String getIcon() {
    return "minecraft:armor_stand";
  }
}
