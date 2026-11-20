package art.arcane.react.content.feature;

import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.platform.MapDataHost;

import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Iris Biome Chunk Share Pie Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIrisBiomeChunkSharePieMap extends ReactFeature implements ReactRenderer, CapabilityGatedFeature {
  public static final String ID = IrisBiomeSharePieRenderer.ID;
  private static final int SAMPLE_INTERVAL_MS = 1000;
  private final transient IrisBiomeSharePieRenderer pie = new IrisBiomeSharePieRenderer(
      MapFeatureHosts::irisBiomes,
      MapFeatureHosts::viewerOnline
  );

  public FeatureIrisBiomeChunkSharePieMap() {
    super(ID);
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return pie.megamapCapability();
  }

  @Override
  public void render() {
    pie.render();
  }

  @Override
  public void onActivate() {
    MapFeatureHosts.data().ifPresent(MapDataHost::startIrisBiomes);
  }

  @Override
  public void onDeactivate() {
    MapFeatureHosts.data().ifPresent(MapDataHost::stopIrisBiomes);
  }

  @Override
  public int getTickInterval() {
    return SAMPLE_INTERVAL_MS;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("iris");
  }

  @Override
  public void onTick() {
    MapFeatureHosts.data().ifPresent(MapDataHost::tickIrisBiomes);
  }
}
