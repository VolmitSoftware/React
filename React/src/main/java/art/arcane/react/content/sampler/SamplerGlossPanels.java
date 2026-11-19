package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossPanels extends RemoteIntegrationSampler {
  public static final String ID = "gloss-panels";

  public SamplerGlossPanels() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_PANELS_ACTIVE, 0, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:lectern";
  }
}
