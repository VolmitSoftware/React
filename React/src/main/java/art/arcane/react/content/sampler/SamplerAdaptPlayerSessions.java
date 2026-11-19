package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerAdaptPlayerSessions extends RemoteIntegrationSampler {
  public static final String ID = "adapt-player-sessions";

  public SamplerAdaptPlayerSessions() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_PLAYER_SESSIONS, 0, " players");
  }

  @Override
  public String getIcon() {
    return "minecraft:player_head";
  }
}
