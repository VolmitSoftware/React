package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossBubbles extends RemoteIntegrationSampler {
  public static final String ID = "gloss-bubbles";

  public SamplerGlossBubbles() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_BUBBLES_PER_SECOND, 1, "/s");
  }

  @Override
  public Material getIcon() {
    return Material.BUBBLE_CORAL;
  }
}
