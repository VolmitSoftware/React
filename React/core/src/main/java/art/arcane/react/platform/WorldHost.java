package art.arcane.react.platform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Loaded worlds and the players in them.
 */
public interface WorldHost {
  List<ReactWorld> worlds();

  Optional<ReactWorld> world(UUID worldId);

  /**
   * The world's namespaced key, such as {@code minecraft:overworld}, as per-world config keys spell it.
   */
  String worldKey(ReactWorld world);

  List<ReactPlayer> onlinePlayers();

  int onlinePlayerCount();

  boolean isOnline(ReactPlayer player);

  Optional<GameLocation> location(ReactPlayer player);

  /**
   * True when any player stands within {@code radius} blocks of the location.
   */
  boolean anyPlayerNear(GameLocation location, double radius);

  /**
   * True once React's player index has seen every online player once.
   */
  boolean isPlayerIndexReady();

  /**
   * Player positions from React's player index; empty until {@link #isPlayerIndexReady()}.
   */
  Optional<List<PlayerPosition>> indexedPlayers();

  /**
   * One player's last indexed position; empty when the index does not know the player yet.
   */
  Optional<PlayerPosition> indexedPlayer(UUID playerId);
}
