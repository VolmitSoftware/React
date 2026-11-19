package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossTablistPlayers extends RemoteIntegrationSampler {
  public static final String ID = "gloss-tablist-players";

  public SamplerGlossTablistPlayers() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_TABLIST_PLAYERS, 0, " players");
  }

  @Override
  public String getIcon() {
    return "minecraft:player_head";
  }
}
