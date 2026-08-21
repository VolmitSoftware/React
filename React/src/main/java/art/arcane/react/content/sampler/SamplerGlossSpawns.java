package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossSpawns extends RemoteIntegrationSampler {
  public static final String ID = "gloss-spawns";

  public SamplerGlossSpawns() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_SPAWNS_PER_SECOND, 1, "/s");
  }

  @Override
  public Material getIcon() {
    return Material.EGG;
  }
}
