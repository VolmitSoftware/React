package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHolouiDisplayEntities extends RemoteIntegrationSampler {
  public static final String ID = "holoui-display-entities";

  public SamplerHolouiDisplayEntities() {
    super(ID, "holoui", IntegrationMetricSchema.HOLOUI_DISPLAY_ENTITIES, 0, " entities");
  }

  @Override
  public Material getIcon() {
    return Material.ARMOR_STAND;
  }
}
