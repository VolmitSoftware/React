package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerAdaptCacheHitRatio extends RemoteIntegrationSampler {
  public static final String ID = "adapt-cache-hit-ratio";

  public SamplerAdaptCacheHitRatio() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_ABILITY_CACHE_HIT_RATIO, 2, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:sculk_sensor";
  }
}
