package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptProvenanceOps extends RemoteIntegrationSampler {
  public static final String ID = "adapt-provenance-ops";

  public SamplerAdaptProvenanceOps() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_PROVENANCE_OPS, 0, "/m");
  }

  @Override
  public Material getIcon() {
    return Material.OBSERVER;
  }
}
