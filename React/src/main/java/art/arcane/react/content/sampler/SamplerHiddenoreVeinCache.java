package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerHiddenoreVeinCache extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-vein-cache";

  public SamplerHiddenoreVeinCache() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_VEIN_CACHE_CHUNKS, 0, " chunks");
  }

  @Override
  public String getIcon() {
    return "minecraft:lodestone";
  }
}
