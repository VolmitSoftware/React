package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHiddenoreBreaks extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-breaks";

  public SamplerHiddenoreBreaks() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_BLOCKS_BROKEN_PER_SECOND, 1, " blk/s");
  }

  @Override
  public Material getIcon() {
    return Material.IRON_PICKAXE;
  }
}
