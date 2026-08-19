package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossPreviewRefresh extends RemoteIntegrationSampler {
  public static final String ID = "gloss-preview-refresh";

  public SamplerGlossPreviewRefresh() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_PREVIEW_REFRESH_PER_SECOND, 1, "/s");
  }

  @Override
  public Material getIcon() {
    return Material.HOPPER;
  }
}
