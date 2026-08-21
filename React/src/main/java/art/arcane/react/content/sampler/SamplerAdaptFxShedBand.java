package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptFxShedBand extends RemoteIntegrationSampler {
  public static final String ID = "adapt-fx-shed-band";

  public SamplerAdaptFxShedBand() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_FX_SHED_BAND, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.DAYLIGHT_DETECTOR;
  }
}
