package art.arcane.react.platform;

import java.util.Optional;
import java.util.UUID;

/**
 * Where a map is looking: the item frame it hangs in, the player holding it, or its world's spawn.
 */
public interface MapAnchorHost {
  MapAnchorHost NONE = new MapAnchorHost() {
    @Override
    public Optional<MapAnchor> anchor(MapRenderTarget target) {
      return Optional.empty();
    }

    @Override
    public boolean frameAnchored(MapRenderTarget target) {
      return false;
    }
  };

  /**
   * Empty when neither the map, its holder nor its frame resolves to a loaded world.
   */
  Optional<MapAnchor> anchor(MapRenderTarget target);

  boolean frameAnchored(MapRenderTarget target);

  /**
   * {@code viewerId} is the online player rendering the map, or null.
   */
  record MapAnchor(UUID worldId, double x, double z, boolean frameAnchored, UUID viewerId) {
  }
}
