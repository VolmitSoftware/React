package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossDisplayEntities extends RemoteIntegrationSampler {
  public static final String ID = "gloss-display-entities";

  public SamplerGlossDisplayEntities() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_DISPLAY_ENTITIES, 0, " entities");
  }

  @Override
  public String getIcon() {
    return "minecraft:armor_stand";
  }
}
