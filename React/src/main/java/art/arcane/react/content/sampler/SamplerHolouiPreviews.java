package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHolouiPreviews extends RemoteIntegrationSampler {
  public static final String ID = "holoui-previews";

  public SamplerHolouiPreviews() {
    super(ID, "holoui", IntegrationMetricSchema.HOLOUI_PREVIEWS_OPEN, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.ITEM_FRAME;
  }
}
