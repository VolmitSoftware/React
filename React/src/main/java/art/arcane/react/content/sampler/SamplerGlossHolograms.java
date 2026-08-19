package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossHolograms extends RemoteIntegrationSampler {
  public static final String ID = "gloss-holograms";

  public SamplerGlossHolograms() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_HOLOGRAMS_ACTIVE, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.BEACON;
  }
}
