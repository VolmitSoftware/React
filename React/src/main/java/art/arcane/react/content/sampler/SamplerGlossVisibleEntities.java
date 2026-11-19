package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossVisibleEntities extends RemoteIntegrationSampler {
  public static final String ID = "gloss-visible-entities";

  public SamplerGlossVisibleEntities() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_DISPLAY_ENTITIES_VISIBLE, 0, " entities");
  }

  @Override
  public String getIcon() {
    return "minecraft:ender_eye";
  }
}
