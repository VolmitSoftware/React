package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossAnimations extends RemoteIntegrationSampler {
  public static final String ID = "gloss-animations";

  public SamplerGlossAnimations() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_ANIMATIONS_ACTIVE, 0, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:firework_rocket";
  }
}
