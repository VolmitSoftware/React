package art.arcane.react.platform;

import art.arcane.react.api.protect.ReactOperation;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Entity lookups and removals for features and actions. Calls that read or change entities run on the thread that
 * owns them; on Folia that is the entity's region.
 */
public interface EntityHost {
  /**
   * Entities in a cube of half-size {@code radius} around the player, excluding the player.
   */
  List<GameEntity> nearby(ReactPlayer player, double radius);

  List<GameEntity> entities(ReactWorld world);

  /**
   * Entities in one loaded chunk; empty when the chunk is not loaded.
   */
  List<GameEntity> chunkEntities(ReactWorld world, int chunkX, int chunkZ);

  /**
   * Entity count of a loaded chunk, or -1 when it is not loaded.
   */
  int chunkEntityCount(UUID worldId, int chunkX, int chunkZ);

  /**
   * Entity count of a world from React's per-tick snapshot.
   */
  int worldEntityCount(UUID worldId);

  /**
   * Every entity type the server knows, in the naming of {@link GameEntity#type()}.
   */
  Collection<String> entityTypes();

  /**
   * React's removal priority for a live entity; lower values are removed first.
   */
  double priority(GameEntity entity);

  /**
   * React's base priority for an entity type before per-entity factors.
   */
  double typePriority(String type);

  /**
   * True when a protection provider or React's own rules shield the entity from the operation.
   */
  boolean isProtected(GameEntity entity, ReactOperation operation);

  /**
   * The final guard right before an entity is changed; false vetoes the operation.
   */
  boolean allows(GameEntity entity, ReactOperation operation);

  /**
   * True when the entity's custom name keeps it from removal under the given setting.
   */
  boolean protectsNamed(GameEntity entity, boolean protectNamedEntities);

  /**
   * True when the entity belongs to a custom-mob plugin or mod.
   */
  boolean isCustomMob(GameEntity entity);

  /**
   * Removes the entity the way React always does, with its drop and death rules.
   */
  void remove(GameEntity entity);

  /**
   * Calls the listener when React's entity ticker refreshes an entity's priority: entities are sampled a few per world
   * each server tick and refreshed at most every ten seconds, on the thread that owns them. An empty collection means
   * every type; types use the naming of {@link GameEntity#type()}.
   */
  Subscription onTick(Collection<String> types, Consumer<GameEntity> listener);

  /**
   * Wraps a platform entity object, as {@link GameEntity#handle()} returns it.
   */
  GameEntity wrap(Object handle);

  /**
   * Entities in a cube of half-size {@code radius} around the entity, excluding it.
   */
  List<GameEntity> nearbyEntities(GameEntity entity, double radius);

  /**
   * Squared distance to the closest player in the entity's world, or positive infinity when none is within
   * {@code maxDistance}.
   */
  double nearestPlayerDistanceSquared(GameEntity entity, double maxDistance);

  /**
   * React's material value of a dropped item's stack times its amount; zero for other entities.
   */
  double itemValue(GameEntity entity);

  /**
   * An integer React stored on the entity under its own key; it survives saves and reloads.
   */
  int persistentInt(GameEntity entity, String key, int fallback);

  void setPersistentInt(GameEntity entity, String key, int value);

  /**
   * A flag React stored on the entity under its own key; it survives saves and reloads.
   */
  boolean persistentFlag(GameEntity entity, String key);

  /**
   * Sets the flag, or removes it when {@code value} is false.
   */
  void setPersistentFlag(GameEntity entity, String key, boolean value);

  /**
   * True when the mob's AI runs; false for entities without AI.
   */
  boolean hasAi(GameEntity entity);

  /**
   * Turns the mob's AI on or off; with it off the mob neither thinks nor moves on its own.
   */
  void setAi(GameEntity entity, boolean ai);

  /**
   * True when the mob is aware, which is Bukkit's {@code Mob#isAware}.
   */
  boolean isAware(GameEntity entity);

  /**
   * An unaware mob keeps its physics but skips its goals, brain and pathfinding.
   */
  void setAware(GameEntity entity, boolean aware);

  // Chunk quarantine culling

  boolean isDead(GameEntity entity);

  boolean isTamed(GameEntity entity);

  boolean isVillager(GameEntity entity);

  /**
   * True for the ender dragon, wither and warden.
   */
  boolean isBoss(GameEntity entity);

  /**
   * Removes the entity at once, without React's countdown.
   */
  void removeNow(GameEntity entity);

  /**
   * Removes the entity with React's visible countdown of {@code seconds}.
   */
  void kill(GameEntity entity, int seconds);
}
