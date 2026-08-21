package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptEventHandlerOps extends RemoteIntegrationSampler {
  public static final String ID = "adapt-event-ops";

  public SamplerAdaptEventHandlerOps() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_EVENT_HANDLER_OPS, 0, "/m");
  }

  @Override
  public Material getIcon() {
    return Material.LEVER;
  }
}
