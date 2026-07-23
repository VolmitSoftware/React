package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptMinions extends RemoteIntegrationSampler {
  public static final String ID = "adapt-minions";

  public SamplerAdaptMinions() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_MINIONS_ACTIVE, 0, " minions");
  }

  @Override
  public Material getIcon() {
    return Material.ZOMBIE_HEAD;
  }
}
