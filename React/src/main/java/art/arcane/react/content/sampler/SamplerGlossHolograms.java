package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossHolograms extends RemoteIntegrationSampler {
  public static final String ID = "gloss-holograms";

  public SamplerGlossHolograms() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_HOLOGRAMS_ACTIVE, 0, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:beacon";
  }
}
