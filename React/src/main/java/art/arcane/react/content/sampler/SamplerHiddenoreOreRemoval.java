package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHiddenoreOreRemoval extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-ore-removal";

  public SamplerHiddenoreOreRemoval() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_ORE_REMOVAL_ENABLED, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.TNT;
  }
}
