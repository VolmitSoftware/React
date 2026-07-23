package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHiddenoreOreRemovalRate extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-ore-removal-rate";

  public SamplerHiddenoreOreRemovalRate() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_ORE_REMOVAL_BLOCKS_PER_SECOND, 0, " blk/s");
  }

  @Override
  public Material getIcon() {
    return Material.SMOOTH_STONE;
  }
}
