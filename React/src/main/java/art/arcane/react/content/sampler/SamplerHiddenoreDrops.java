package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerHiddenoreDrops extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-drops";

  public SamplerHiddenoreDrops() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_DROPS_INJECTED_PER_SECOND, 1, "/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:raw_iron";
  }
}
