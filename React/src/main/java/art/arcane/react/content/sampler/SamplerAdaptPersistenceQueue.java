package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerAdaptPersistenceQueue extends RemoteIntegrationSampler {
  public static final String ID = "adapt-persistence-queue";

  public SamplerAdaptPersistenceQueue() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_PERSISTENCE_QUEUE_DEPTH, 0, "");
  }

  @Override
  public String getIcon() {
    return "minecraft:writable_book";
  }
}
