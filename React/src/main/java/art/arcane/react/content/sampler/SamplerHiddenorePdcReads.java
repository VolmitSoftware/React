package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHiddenorePdcReads extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-pdc-reads";

  public SamplerHiddenorePdcReads() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_PDC_READS_PER_SECOND, 1, "/s");
  }

  @Override
  public Material getIcon() {
    return Material.LECTERN;
  }
}
