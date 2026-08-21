package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHiddenoreDrops extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-drops";

  public SamplerHiddenoreDrops() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_DROPS_INJECTED_PER_SECOND, 1, "/s");
  }

  @Override
  public Material getIcon() {
    return Material.RAW_IRON;
  }
}
