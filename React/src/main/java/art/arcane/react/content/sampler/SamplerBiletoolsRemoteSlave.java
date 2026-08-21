package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerBiletoolsRemoteSlave extends RemoteIntegrationSampler {
  public static final String ID = "biletools-remote-slave";

  public SamplerBiletoolsRemoteSlave() {
    super(ID, "biletools", IntegrationMetricSchema.BILETOOLS_REMOTE_SLAVE_ONLINE, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.IRON_CHAIN;
  }
}
