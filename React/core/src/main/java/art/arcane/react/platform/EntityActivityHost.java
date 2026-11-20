package art.arcane.react.platform;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What React's entity sleep, activation and pathfinding features need beyond {@link EntityHost}: budgeted world
 * scans, entity traits, wake events, mob navigation budgets and per-world entity ranges. Calls that read or change
 * an entity run on the thread that owns it; listeners run on the thread that fired the event.
 */
public interface EntityActivityHost {
  /**
   * Up to {@code budget} entities of the world, continuing where the previous call for that world stopped.
   */
  List<GameEntity> nextEntities(ReactWorld world, int budget);

  /**
   * Up to {@code limit} entities around the player that {@code accept} lets through, or all of them when it is null,
   * for a scan named {@code owner}. Call it on the player's thread.
   */
  List<GameEntity> sampleNearby(ReactPlayer player, String owner, double horizontalRadius, double verticalRadius,
                                int limit, Predicate<GameEntity> accept);

  /**
   * True when the entity carries a custom name that is not blank.
   */
  boolean isNamed(GameEntity entity);

  boolean isVillager(GameEntity entity);

  /**
   * True for the ender dragon, the wither and the warden.
   */
  boolean isBoss(GameEntity entity);

  /**
   * True when the entity is saved with its chunk.
   */
  boolean isPersistent(GameEntity entity);

  /**
   * The entity's numeric id for this server session.
   */
  int entityId(GameEntity entity);

  /**
   * True when mobs can be made unaware, which entity dozing relies on.
   */
  boolean supportsAwareness();

  /**
   * Fires when an entity takes damage that was not cancelled.
   */
  Subscription onDamage(Consumer<GameEntity> listener);

  /**
   * Fires when an entity picks or drops a target that was not cancelled; the target is null when it was dropped.
   */
  Subscription onTarget(BiConsumer<GameEntity, GameEntity> listener);

  /**
   * Fires with the entities of a chunk after they load.
   */
  Subscription onEntitiesLoad(Consumer<List<GameEntity>> listener);

  /**
   * Fires with the entities of a chunk before they unload.
   */
  Subscription onEntitiesUnload(Consumer<List<GameEntity>> listener);

  /**
   * True when mob path searches can be given a smaller node budget.
   */
  boolean supportsNavigationBudget();

  /**
   * Scales the mob's path search node budget and marks it as budgeted; false when the mob cannot take a budget.
   */
  boolean setNavigationBudget(GameEntity mob, float multiplier);

  /**
   * Restores the mob's full path search budget and clears its mark. The mark is cleared even when the restore throws.
   */
  void resetNavigationBudget(GameEntity mob);

  /**
   * True when the mob carries React's budget mark.
   */
  boolean hasNavigationBudget(GameEntity mob);

  /**
   * The world's entity ranges; empty where this server software has none to change.
   */
  Optional<EntityRanges> entityRanges(ReactWorld world);
}
