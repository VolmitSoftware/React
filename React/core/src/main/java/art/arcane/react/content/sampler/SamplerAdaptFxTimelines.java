package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerAdaptFxTimelines extends RemoteIntegrationSampler {
  public static final String ID = "adapt-fx-timelines";

  public SamplerAdaptFxTimelines() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_FX_TIMELINES_ACTIVE, 0, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:firework_rocket";
  }
}
