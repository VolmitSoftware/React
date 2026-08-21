package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHiddenoreVeinComputes extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-vein-computes";

  public SamplerHiddenoreVeinComputes() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_VEIN_CHUNKS_COMPUTED_PER_SECOND, 1, " chk/s");
  }

  @Override
  public Material getIcon() {
    return Material.DEEPSLATE;
  }
}
