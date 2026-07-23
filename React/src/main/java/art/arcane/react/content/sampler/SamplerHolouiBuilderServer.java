package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHolouiBuilderServer extends RemoteIntegrationSampler {
  public static final String ID = "holoui-builder-server";

  public SamplerHolouiBuilderServer() {
    super(ID, "holoui", IntegrationMetricSchema.HOLOUI_BUILDER_SERVER_RUNNING, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.COMMAND_BLOCK;
  }
}
