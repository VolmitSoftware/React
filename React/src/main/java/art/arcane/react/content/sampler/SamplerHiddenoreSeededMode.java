package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerHiddenoreSeededMode extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-seeded-mode";

  public SamplerHiddenoreSeededMode() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_SEEDED_MODE, 0, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:wheat_seeds";
  }
}
