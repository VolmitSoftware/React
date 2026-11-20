package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.platform.MapRenderTarget;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class BukkitMapAnchorHost implements MapAnchorHost {
  public static final BukkitMapAnchorHost INSTANCE = new BukkitMapAnchorHost();

  private BukkitMapAnchorHost() {
  }

  @Override
  public Optional<MapAnchor> anchor(MapRenderTarget target) {
    Player viewer = BukkitMapTargets.player(target);
    MapController mapController = React.controller(MapController.class);
    Location anchor = mapController == null
        ? viewer == null ? null : viewer.getLocation().clone()
        : mapController.resolveMapAnchor(target);
    World targetWorld = BukkitMapTargets.world(target);
    World mapWorld = targetWorld != null
        ? targetWorld
        : anchor == null ? viewer == null ? null : viewer.getWorld() : anchor.getWorld();
    if (mapWorld == null) {
      return Optional.empty();
    }

    Location center = anchor == null || anchor.getWorld() == null ? mapWorld.getSpawnLocation() : anchor;
    boolean frameAnchored = mapController != null && mapController.hasFrameAnchor(target);
    return Optional.of(new MapAnchor(
        mapWorld.getUID(),
        center.getX(),
        center.getZ(),
        frameAnchored,
        viewer == null ? null : viewer.getUniqueId()
    ));
  }

  @Override
  public boolean frameAnchored(MapRenderTarget target) {
    MapController mapController = React.controller(MapController.class);
    return mapController != null && mapController.hasFrameAnchor(target);
  }
}
