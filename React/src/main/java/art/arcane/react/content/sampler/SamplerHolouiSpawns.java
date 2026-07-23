package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHolouiSpawns extends RemoteIntegrationSampler {
  public static final String ID = "holoui-spawns";

  public SamplerHolouiSpawns() {
    super(ID, "holoui", IntegrationMetricSchema.HOLOUI_SPAWNS_PER_SECOND, 1, "/s");
  }

  @Override
  public Material getIcon() {
    return Material.EGG;
  }
}
