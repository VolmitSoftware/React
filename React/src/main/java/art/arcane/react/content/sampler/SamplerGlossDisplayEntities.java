package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossDisplayEntities extends RemoteIntegrationSampler {
  public static final String ID = "gloss-display-entities";

  public SamplerGlossDisplayEntities() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_DISPLAY_ENTITIES, 0, " entities");
  }

  @Override
  public Material getIcon() {
    return Material.ARMOR_STAND;
  }
}
