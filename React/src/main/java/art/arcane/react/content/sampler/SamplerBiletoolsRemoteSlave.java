package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerBiletoolsRemoteSlave extends RemoteIntegrationSampler {
  public static final String ID = "biletools-remote-slave";

  public SamplerBiletoolsRemoteSlave() {
    super(ID, "biletools", IntegrationMetricSchema.BILETOOLS_REMOTE_SLAVE_ONLINE, 0, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:iron_chain";
  }
}
