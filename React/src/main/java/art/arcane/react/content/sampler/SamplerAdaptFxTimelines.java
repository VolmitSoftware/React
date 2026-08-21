package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptFxTimelines extends RemoteIntegrationSampler {
  public static final String ID = "adapt-fx-timelines";

  public SamplerAdaptFxTimelines() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_FX_TIMELINES_ACTIVE, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.FIREWORK_ROCKET;
  }
}
