package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerHiddenoreVeinDiscoveries extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-vein-discoveries";

  public SamplerHiddenoreVeinDiscoveries() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_VEINS_DISCOVERED_PER_SECOND, 2, "/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:diamond_ore";
  }
}
