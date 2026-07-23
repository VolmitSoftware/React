package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerBiletoolsWatchedJars extends RemoteIntegrationSampler {
  public static final String ID = "biletools-watched-jars";

  public SamplerBiletoolsWatchedJars() {
    super(ID, "biletools", IntegrationMetricSchema.BILETOOLS_WATCHED_JARS, 0, " jars");
  }

  @Override
  public Material getIcon() {
    return Material.OBSERVER;
  }
}
