package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHolouiPreviewRefresh extends RemoteIntegrationSampler {
  public static final String ID = "holoui-preview-refresh";

  public SamplerHolouiPreviewRefresh() {
    super(ID, "holoui", IntegrationMetricSchema.HOLOUI_PREVIEW_REFRESH_PER_SECOND, 1, "/s");
  }

  @Override
  public Material getIcon() {
    return Material.HOPPER;
  }
}
