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

  /**
   * True for living entities: mobs, players, armor stands.
   */
  default boolean isLiving() {
    return false;
  }

  /**
   * True for entities driven by AI goals or brains, the ones React can pause and doze.
   */
  default boolean isMob() {
    return false;
  }

  /**
   * True for hostile monsters, Bukkit's {@code Monster}.
   */
  default boolean isMonster() {
    return false;
  }

  /**
   * Length of the entity's velocity in blocks per tick.
   */
  default double speed() {
    return 0D;
  }

  default double health() {
    return 0D;
  }

  /**
   * The max health attribute, or NaN when the entity has none.
   */
  default double maxHealth() {
    return Double.NaN;
  }

  default boolean isTameable() {
    return false;
  }

  default boolean isTamed() {
    return false;
  }

  /**
   * True for entities with a baby and an adult stage.
   */
  default boolean isBreedable() {
    return false;
  }

  default boolean isAdult() {
    return true;
  }

  default boolean isAgeLocked() {
    return false;
  }

  Object handle();
}
