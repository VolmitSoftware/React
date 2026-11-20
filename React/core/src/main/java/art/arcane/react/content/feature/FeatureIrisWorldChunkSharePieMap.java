package art.arcane.react.content.feature;

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;


@art.arcane.react.util.project.config.ConfigDescription("Configuration for Iris World Chunk Share Pie Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIrisWorldChunkSharePieMap extends ReactFeature implements ReactRenderer {
  public static final String ID = IrisWorldSharePieRenderer.ID;
  private final transient IrisWorldSharePieRenderer renderer = new IrisWorldSharePieRenderer(
      MapFeatureHosts.integrations(),
      MapFeatureHosts::viewerOnline
  );

  public FeatureIrisWorldChunkSharePieMap() {
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
