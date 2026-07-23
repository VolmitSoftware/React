package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHolouiSessions extends RemoteIntegrationSampler {
  public static final String ID = "holoui-sessions";

  public SamplerHolouiSessions() {
    super(ID, "holoui", IntegrationMetricSchema.HOLOUI_SESSION_HOLDERS, 0, " players");
  }

  @Override
  public Material getIcon() {
    return Material.GLOW_ITEM_FRAME;
  }
}
