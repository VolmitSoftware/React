package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHiddenoreReloads extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-reloads";

  public SamplerHiddenoreReloads() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_CONFIG_RELOADS_TOTAL, 0, " reloads");
  }

  @Override
  public Material getIcon() {
    return Material.REDSTONE;
  }
}
