package art.arcane.react.content.feature;

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.platform.cost.OwnerNoun;


@art.arcane.react.util.project.config.ConfigDescription(
    value = "Configuration for Plugin Event Impact Pie Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.",
    mod = "Configuration for the Mod Impact Pie Map feature: each mod's share of total server cost across events, entities, block entities and world generation."
)
public class FeaturePluginEventImpactPieMap extends ReactFeature implements ReactRenderer {
  public static final String ID = OwnerImpactPieRenderer.ID;
  private final transient OwnerImpactPieRenderer renderer = new OwnerImpactPieRenderer(
      MapFeatureHosts::events,
      MapFeatureHosts::viewerOnline
  );

  public FeaturePluginEventImpactPieMap() {
    super(ID);
  }

  @Override
  public String getName() {
    return OwnerNoun.modded() ? "Mod Impact Pie Map" : super.getName();
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
