package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerBiletoolsReloads extends RemoteIntegrationSampler {
  public static final String ID = "biletools-reloads";

  public SamplerBiletoolsReloads() {
    super(ID, "biletools", IntegrationMetricSchema.BILETOOLS_RELOADS_TOTAL, 0, " reloads");
  }

  @Override
  public Material getIcon() {
    return Material.LIME_DYE;
  }
}
