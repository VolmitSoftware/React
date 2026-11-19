package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossMenuDefinitions extends RemoteIntegrationSampler {
  public static final String ID = "gloss-menu-definitions";

  public SamplerGlossMenuDefinitions() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_MENU_DEFINITIONS, 0, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:bookshelf";
  }
}
