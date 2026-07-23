package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHolouiMenus extends RemoteIntegrationSampler {
  public static final String ID = "holoui-menus";

  public SamplerHolouiMenus() {
    super(ID, "holoui", IntegrationMetricSchema.HOLOUI_MENUS_OPEN, 0, " menus");
  }

  @Override
  public Material getIcon() {
    return Material.PAINTING;
  }
}
