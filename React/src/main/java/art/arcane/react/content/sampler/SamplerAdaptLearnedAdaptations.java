package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptLearnedAdaptations extends RemoteIntegrationSampler {
  public static final String ID = "adapt-learned-adaptations";

  public SamplerAdaptLearnedAdaptations() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_LEARNED_ADAPTATIONS_ONLINE, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.ENCHANTED_BOOK;
  }
}
