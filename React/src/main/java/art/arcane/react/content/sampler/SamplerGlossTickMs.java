package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossTickMs extends RemoteIntegrationSampler {
  public static final String ID = "gloss-tick-ms";

  public SamplerGlossTickMs() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_TICK_MS, 2, " ms/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:clock";
  }
}
