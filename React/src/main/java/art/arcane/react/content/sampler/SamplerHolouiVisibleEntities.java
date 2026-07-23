package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHolouiVisibleEntities extends RemoteIntegrationSampler {
  public static final String ID = "holoui-visible-entities";

  public SamplerHolouiVisibleEntities() {
    super(ID, "holoui", IntegrationMetricSchema.HOLOUI_DISPLAY_ENTITIES_VISIBLE, 0, " entities");
  }

  @Override
  public Material getIcon() {
    return Material.ENDER_EYE;
  }
}
