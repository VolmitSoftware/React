package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossPreviews extends RemoteIntegrationSampler {
  public static final String ID = "gloss-previews";

  public SamplerGlossPreviews() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_PREVIEWS_OPEN, 0, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:item_frame";
  }
}
