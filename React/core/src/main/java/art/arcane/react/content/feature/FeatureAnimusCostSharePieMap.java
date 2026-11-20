package art.arcane.react.content.feature;

import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;

import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for the Animus Cost Share Pie Map feature.")
public class FeatureAnimusCostSharePieMap extends ReactFeature implements ReactRenderer, CapabilityGatedFeature {
  public static final String ID = AnimusCostSharePieRenderer.ID;
  private final transient AnimusCostSharePieRenderer renderer = new AnimusCostSharePieRenderer(
      MapFeatureHosts::animus,
      MapFeatureHosts::viewerOnline
  );

  public FeatureAnimusCostSharePieMap() {
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

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of(AnimusMetrics.SOURCE_ID);
  }
}
