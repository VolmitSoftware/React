package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossPreviews extends RemoteIntegrationSampler {
  public static final String ID = "gloss-previews";

  public SamplerGlossPreviews() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_PREVIEWS_OPEN, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.ITEM_FRAME;
  }
}
