package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptPersistenceQueue extends RemoteIntegrationSampler {
  public static final String ID = "adapt-persistence-queue";

  public SamplerAdaptPersistenceQueue() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_PERSISTENCE_QUEUE_DEPTH, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.WRITABLE_BOOK;
  }
}
