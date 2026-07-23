package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHolouiTickMs extends RemoteIntegrationSampler {
  public static final String ID = "holoui-tick-ms";

  public SamplerHolouiTickMs() {
    super(ID, "holoui", IntegrationMetricSchema.HOLOUI_TICK_MS, 2, " ms");
  }

  @Override
  public Material getIcon() {
    return Material.CLOCK;
  }
}
