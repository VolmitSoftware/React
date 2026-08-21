package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptFxPackets extends RemoteIntegrationSampler {
  public static final String ID = "adapt-fx-packets";

  public SamplerAdaptFxPackets() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_FX_PACKETS_USED, 0, " pkts");
  }

  @Override
  public Material getIcon() {
    return Material.FIREWORK_STAR;
  }
}
