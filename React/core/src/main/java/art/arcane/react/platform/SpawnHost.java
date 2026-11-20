package art.arcane.react.platform;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Creature spawns and other entity additions, before the entity joins the world. {@link Spawn#reason()} uses
 * Bukkit's spawn reason names, such as {@code NATURAL}, {@code SPAWNER} or {@code TRIAL_SPAWNER}, on every platform.
 */
public interface SpawnHost {
  interface Spawn extends HostEvent {
    GameEntity entity();

    GameLocation location();

    /**
     * The entity type in the naming of {@link GameEntity#type()}.
     */
    String type();

    String reason();

    boolean isMonster();

    /**
     * True when the entity carries a custom name that is not blank.
     */
    boolean hasCustomName();
  }

  /**
   * An entity other than a creature joining the world, such as an item, an armor stand or a vehicle.
   */
  interface EntitySpawn extends HostEvent {
    GameEntity entity();

    GameLocation location();

    String type();

    boolean isItem();

    /**
     * True when a creature spawn event for the same entity follows this one, as for Bukkit's spawner events.
     */
    boolean precedesAdd();
  }

  /**
   * A baby about to be born from breeding; the location and type are the baby's.
   */
  interface Breed extends HostEvent {
    GameLocation location();

    String type();
  }

  /**
   * A player dropping an item; the location is the player's.
   */
  interface ItemDrop extends HostEvent {
    GameLocation location();
  }

  @FunctionalInterface
  interface CellVisitor {
    void visit(int x, int y, int z);
  }

  Subscription onCreatureSpawn(ListenerPriority priority, boolean ignoreCancelled, Consumer<Spawn> listener);

  Subscription onEntitySpawn(ListenerPriority priority, boolean ignoreCancelled, Consumer<EntitySpawn> listener);

  Subscription onBreed(ListenerPriority priority, boolean ignoreCancelled, Consumer<Breed> listener);

  Subscription onPlayerDropItem(ListenerPriority priority, boolean ignoreCancelled, Consumer<ItemDrop> listener);

  /**
   * Entities in a loaded chunk, leaving out item entities unless {@code countItems}; -1 when the chunk or its
   * entities are not loaded. Never loads the chunk.
   */
  int chunkEntityCount(UUID worldId, int chunkX, int chunkZ, boolean countItems);

  /**
   * True when a protection provider shields spawns of this type at the location; {@code reason} may be null.
   */
  boolean spawnProtected(String type, GameLocation location, String reason);

  /**
   * Pushes the next spawn attempt of every mob spawner that can place mobs at the location to at least
   * {@code delayTicks} away.
   */
  void backoffSpawners(GameLocation location, int delayTicks);

  /**
   * Visits every loaded air cell in the box around {@code center} whose light level is at most {@code maxLight}.
   */
  void forEachDarkAir(GameLocation center, int horizontalRadius, int verticalRadius, int maxLight, CellVisitor visitor);

  static boolean isSpawnerReason(String reason) {
    return "SPAWNER".equals(reason) || "TRIAL_SPAWNER".equals(reason);
  }

  static boolean isNaturalReason(String reason) {
    if (reason == null) {
      return false;
    }
    return switch (reason) {
      case "NATURAL", "NETHER_PORTAL", "REINFORCEMENTS", "JOCKEY", "PATROL", "RAID" -> true;
      default -> false;
    };
  }
}
