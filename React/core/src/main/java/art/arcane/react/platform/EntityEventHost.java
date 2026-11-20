package art.arcane.react.platform;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Entity damage and projectile events, plus the entity checks the cleanup tweaks share. Listeners run on the thread
 * that owns the entity.
 */
public interface EntityEventHost {
  interface Damage {
    GameEntity entity();

    /**
     * True for damage from standing on fire, Bukkit's {@code FIRE_TICK}.
     */
    boolean isFireTick();
  }

  interface Launch {
    GameEntity projectile();

    /**
     * The player that shot the projectile, when a player did.
     */
    Optional<ReactPlayer> shooter();

    void cancel();
  }

  Subscription onDamage(Consumer<Damage> listener);

  /**
   * Projectiles about to enter the world. A cancelled launch keeps the projectile out of it.
   */
  Subscription onLaunch(Consumer<Launch> listener);

  boolean isMonster(GameEntity entity);

  /**
   * True when the entity sits in water over a bubble column or soul sand.
   */
  boolean isBubbled(GameEntity entity);

  boolean nearPlayer(GameEntity entity, double radius);

  /**
   * Removes the entity after React's visible countdown of {@code seconds}.
   */
  void removeLater(GameEntity entity, int seconds);
}
