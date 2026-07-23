package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHiddenoreSeededMode extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-seeded-mode";

  public SamplerHiddenoreSeededMode() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_SEEDED_MODE, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.WHEAT_SEEDS;
  }
}
