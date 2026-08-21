package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossTickMs extends RemoteIntegrationSampler {
  public static final String ID = "gloss-tick-ms";

  public SamplerGlossTickMs() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_TICK_MS, 2, " ms");
  }

  @Override
  public Material getIcon() {
    return Material.CLOCK;
  }
}
