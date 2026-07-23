package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptSpatialTickets extends RemoteIntegrationSampler {
  public static final String ID = "adapt-spatial-tickets";

  public SamplerAdaptSpatialTickets() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_SPATIAL_XP_TICKETS, 0, " orbs");
  }

  @Override
  public Material getIcon() {
    return Material.EXPERIENCE_BOTTLE;
  }
}
