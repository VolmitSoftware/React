package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptAbilityChecksPerTick extends RemoteIntegrationSampler {
  public static final String ID = "adapt-ability-checks-per-tick";

  public SamplerAdaptAbilityChecksPerTick() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS_TICK, 3, " op/tick");
  }

  @Override
  public Material getIcon() {
    return Material.BOOKSHELF;
  }
}
