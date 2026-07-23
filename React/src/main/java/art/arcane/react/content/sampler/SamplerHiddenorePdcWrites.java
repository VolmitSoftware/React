package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHiddenorePdcWrites extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-pdc-writes";

  public SamplerHiddenorePdcWrites() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_PDC_WRITES_PER_SECOND, 1, "/s");
  }

  @Override
  public Material getIcon() {
    return Material.ANVIL;
  }
}
