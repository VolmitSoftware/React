package art.arcane.react.platform;

import java.util.UUID;

/**
 * A live entity as React's features see it. {@link #handle()} is the platform's own entity object and only means
 * something to the platform module that produced it. Read it on the thread that owns the entity.
 */
public interface GameEntity {
  UUID id();

  /**
   * The entity type as React's config files name it: the Bukkit {@code EntityType} constant for vanilla types, such
   * as {@code ITEM_DISPLAY}, and the namespaced id for modded ones, such as {@code create:contraption}.
   */
  String type();

  GameLocation location();

  boolean isValid();

  int ticksLived();

  boolean hasCustomName();

  boolean isPlayer();

  Object handle();
}
