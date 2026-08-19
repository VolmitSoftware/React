package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossBuilderServer extends RemoteIntegrationSampler {
  public static final String ID = "gloss-builder-server";

  public SamplerGlossBuilderServer() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_BUILDER_SERVER_RUNNING, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.COMMAND_BLOCK;
  }
}
