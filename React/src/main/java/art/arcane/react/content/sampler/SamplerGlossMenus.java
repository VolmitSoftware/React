package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossMenus extends RemoteIntegrationSampler {
  public static final String ID = "gloss-menus";

  public SamplerGlossMenus() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_MENUS_OPEN, 0, " menus");
  }

  @Override
  public Material getIcon() {
    return Material.PAINTING;
  }
}
