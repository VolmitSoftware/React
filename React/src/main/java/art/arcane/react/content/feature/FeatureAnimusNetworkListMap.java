package art.arcane.react.content.feature;

import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.platform.bukkit.BukkitMapAnchorHost;

import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for the Animus Network List Map feature.")
public class FeatureAnimusNetworkListMap extends ReactFeature implements ReactRenderer, CapabilityGatedFeature {
  public static final String ID = AnimusNetworkListRenderer.ID;
  private final transient AnimusNetworkListRenderer renderer =
      new AnimusNetworkListRenderer(AnimusMetricSeries::snapshot, BukkitMapAnchorHost.INSTANCE);

  public FeatureAnimusNetworkListMap() {
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
