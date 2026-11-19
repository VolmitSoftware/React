package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerBiletoolsDirtyPlugins extends RemoteIntegrationSampler {
  public static final String ID = "biletools-dirty-plugins";

  public SamplerBiletoolsDirtyPlugins() {
    super(ID, "biletools", IntegrationMetricSchema.BILETOOLS_DIRTY_PLUGINS, 0, " plugins");
  }

  @Override
  public String getIcon() {
    return "minecraft:poisonous_potato";
  }
}
