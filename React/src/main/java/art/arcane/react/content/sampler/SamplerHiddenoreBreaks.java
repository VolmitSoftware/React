package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerHiddenoreBreaks extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-breaks";

  public SamplerHiddenoreBreaks() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_BLOCKS_BROKEN_PER_SECOND, 1, " blk/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:iron_pickaxe";
  }
}
