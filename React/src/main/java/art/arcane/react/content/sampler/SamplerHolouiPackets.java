package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHolouiPackets extends RemoteIntegrationSampler {
  public static final String ID = "holoui-packets";

  public SamplerHolouiPackets() {
    super(ID, "holoui", IntegrationMetricSchema.HOLOUI_PACKETS_PER_SECOND, 0, " pkt/s");
  }

  @Override
  public Material getIcon() {
    return Material.REPEATER;
  }
}
