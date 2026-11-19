package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossEmoji extends RemoteIntegrationSampler {
  public static final String ID = "gloss-emoji";

  public SamplerGlossEmoji() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_EMOJI_REPLACEMENTS_PER_SECOND, 1, "/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:sunflower";
  }
}
