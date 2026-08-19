package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossTablistPlayers extends RemoteIntegrationSampler {
  public static final String ID = "gloss-tablist-players";

  public SamplerGlossTablistPlayers() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_TABLIST_PLAYERS, 0, " players");
  }

  @Override
  public Material getIcon() {
    return Material.PLAYER_HEAD;
  }
}
