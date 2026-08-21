package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossAnimations extends RemoteIntegrationSampler {
  public static final String ID = "gloss-animations";

  public SamplerGlossAnimations() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_ANIMATIONS_ACTIVE, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.FIREWORK_ROCKET;
  }
}
