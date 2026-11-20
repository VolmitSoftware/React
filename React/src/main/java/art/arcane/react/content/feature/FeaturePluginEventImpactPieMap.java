package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.EventController;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Plugin Event Impact Pie Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeaturePluginEventImpactPieMap extends ReactFeature implements ReactRenderer {
  public static final String ID = OwnerImpactPieRenderer.ID;
  private final transient OwnerImpactPieRenderer renderer = new OwnerImpactPieRenderer(
      () -> React.controller(EventController.class),
      (UUID viewerId) -> {
        Player viewer = Bukkit.getPlayer(viewerId);
        return viewer != null && viewer.isOnline();
      }
  );

  public FeaturePluginEventImpactPieMap() {
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
