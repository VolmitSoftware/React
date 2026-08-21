package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerGlossBoards extends RemoteIntegrationSampler {
  public static final String ID = "gloss-boards";

  public SamplerGlossBoards() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_BOARDS_ACTIVE, 0, "");
  }

  @Override
  public Material getIcon() {
    return Material.OAK_SIGN;
  }
}
