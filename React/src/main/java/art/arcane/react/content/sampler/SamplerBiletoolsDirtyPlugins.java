package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerBiletoolsDirtyPlugins extends RemoteIntegrationSampler {
  public static final String ID = "biletools-dirty-plugins";

  public SamplerBiletoolsDirtyPlugins() {
    super(ID, "biletools", IntegrationMetricSchema.BILETOOLS_DIRTY_PLUGINS, 0, " plugins");
  }

  @Override
  public Material getIcon() {
    return Material.POISONOUS_POTATO;
  }
}
