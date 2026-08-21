package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptPlayerSessions extends RemoteIntegrationSampler {
  public static final String ID = "adapt-player-sessions";

  public SamplerAdaptPlayerSessions() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_PLAYER_SESSIONS, 0, " players");
  }

  @Override
  public Material getIcon() {
    return Material.PLAYER_HEAD;
  }
}
