package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHolouiMenuDefinitions extends RemoteIntegrationSampler {
  public static final String ID = "holoui-menu-definitions";

  public SamplerHolouiMenuDefinitions() {
    super(ID, "holoui", IntegrationMetricSchema.HOLOUI_MENU_DEFINITIONS, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.BOOKSHELF;
  }
}
