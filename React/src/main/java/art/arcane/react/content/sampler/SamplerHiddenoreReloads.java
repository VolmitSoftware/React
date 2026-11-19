package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerHiddenoreReloads extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-reloads";

  public SamplerHiddenoreReloads() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_CONFIG_RELOADS_TOTAL, 0, " reloads");
  }

  @Override
  public String getIcon() {
    return "minecraft:redstone";
  }
}
