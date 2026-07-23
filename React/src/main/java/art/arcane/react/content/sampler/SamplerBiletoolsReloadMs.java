package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerBiletoolsReloadMs extends RemoteIntegrationSampler {
  public static final String ID = "biletools-reload-ms";

  public SamplerBiletoolsReloadMs() {
    super(ID, "biletools", IntegrationMetricSchema.BILETOOLS_LAST_RELOAD_MS, 0, " ms");
  }

  @Override
  public Material getIcon() {
    return Material.CLOCK;
  }
}
