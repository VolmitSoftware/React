package art.arcane.react.platform;

import art.arcane.react.api.protect.ReactOperation;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

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
}
