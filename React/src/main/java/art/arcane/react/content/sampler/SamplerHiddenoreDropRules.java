package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerHiddenoreDropRules extends RemoteIntegrationSampler {
  public static final String ID = "hiddenore-drop-rules";

  public SamplerHiddenoreDropRules() {
    super(ID, "hiddenore", IntegrationMetricSchema.HIDDENORE_DROP_RULES, 0, " rules");
  }

  @Override
  public Material getIcon() {
    return Material.BOOK;
  }
}
