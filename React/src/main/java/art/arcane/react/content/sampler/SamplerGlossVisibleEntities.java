package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossVisibleEntities extends RemoteIntegrationSampler {
  public static final String ID = "gloss-visible-entities";

  public SamplerGlossVisibleEntities() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_DISPLAY_ENTITIES_VISIBLE, 0, " entities");
  }

  @Override
  public Material getIcon() {
    return Material.ENDER_EYE;
  }
}
