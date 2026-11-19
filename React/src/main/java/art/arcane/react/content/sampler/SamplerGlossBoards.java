package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossBoards extends RemoteIntegrationSampler {
  public static final String ID = "gloss-boards";

  public SamplerGlossBoards() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_BOARDS_ACTIVE, 0, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:oak_sign";
  }
}
