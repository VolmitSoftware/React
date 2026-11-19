package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerAdaptProvenanceOps extends RemoteIntegrationSampler {
  public static final String ID = "adapt-provenance-ops";

  public SamplerAdaptProvenanceOps() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_PROVENANCE_OPS, 0, "/m");
  }

  @Override
  public String getIcon() {
    return "minecraft:observer";
  }
}
