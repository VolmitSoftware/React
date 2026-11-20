package art.arcane.react.content.feature;

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for the Adapt Ability Impact List Map feature.")
public class FeatureAdaptAbilityImpactListMap extends ReactFeature implements ReactRenderer {
  public static final String ID = AdaptAbilityImpactListRenderer.ID;

  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum rows drawn per map tile, or 0 to fill the available height.", impact = "Zero lets a taller map wall list every ability; a fixed value multiplies by the number of vertical tiles, so a taller wall still lists more abilities.")
  private int maxRowsPerTile = 0;
  private final transient AdaptAbilityImpactListRenderer renderer = new AdaptAbilityImpactListRenderer(
      MapFeatureHosts.integrations(),
      MapFeatureHosts.anchors(),
      () -> maxRowsPerTile
  );

  public FeatureAdaptAbilityImpactListMap() {
    super(ID);
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return renderer.megamapCapability();
  }

  @Override
  public void render() {
    renderer.render();
  }
}
