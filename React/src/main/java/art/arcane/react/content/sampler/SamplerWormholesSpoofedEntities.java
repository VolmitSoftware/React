package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerWormholesSpoofedEntities extends RemoteIntegrationSampler {
  public static final String ID = "wormholes-spoofed-entities";

  public SamplerWormholesSpoofedEntities() {
    super(ID, "wormholes", IntegrationMetricSchema.WORMHOLES_SPOOFED_ENTITIES, 0, " ent");
  }

  @Override
  public String getIcon() {
    return "minecraft:armor_stand";
  }
}
