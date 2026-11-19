package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossSessions extends RemoteIntegrationSampler {
  public static final String ID = "gloss-sessions";

  public SamplerGlossSessions() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_SESSION_HOLDERS, 0, " players");
  }

  @Override
  public String getIcon() {
    return "minecraft:glow_item_frame";
  }
}
