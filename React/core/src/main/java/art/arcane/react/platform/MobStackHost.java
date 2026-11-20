package art.arcane.react.platform;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mob state, spawning, naming and events for mob stacking. Calls that read or change an entity run on the thread
 * that owns it. Names are legacy section-coded strings.
 */
public interface MobStackHost {
  String DO_NOT_STACK = "DoNotStack";
  String UNIQUE_STACK = "UniqueMobStack";
  String SPAWNED_BY_SPAWNER = "SpawnedBySpawner";

  /**
   * Mob stacking events. Listeners run on the thread that owns the entity, after the platform's own handling unless
   * a method says otherwise.
   */
  interface Events {
    /**
     * Any entity entering a world, after the spawn was allowed.
     */
    void onSpawn(GameEntity entity);

    /**
     * A mob spawner created the entity; runs before {@link #onSpawn}.
     */
    void onSpawnerSpawn(GameEntity entity);

    void onDeath(GameEntity entity);

    void onTame(GameEntity entity, UUID ownerId);

    /**
     * A sneaking player right-clicked the entity with the main hand.
     */
    void onSneakInteract(GameEntity entity);

    /**
     * Runs before a creeper is charged or uncharged or an enderman picks up or places a block. False cancels the
     * change.
     */
    boolean beforeStateChange(GameEntity entity);

    /**
     * The entity left its world; {@code exploded} is true when a creeper removed itself by exploding.
     */
    void onRemove(GameEntity entity, boolean exploded);

    /**
     * Entities loaded from disk with their chunk.
     */
    void onEntitiesLoad(List<GameEntity> entities);
  }

  /**
   * Feeds the spawn events of {@link Events} from the platform's spawn host, at the priorities Bukkit's mob stacking
   * listened at: spawner spawns at NORMAL, every allowed entity spawn at MONITOR.
   */
  static Subscription spawnEvents(SpawnHost spawns, Events events) {
    if (spawns == null || events == null) {
      return Subscription.NONE;
    }
    Subscription spawner = spawns.onCreatureSpawn(ListenerPriority.NORMAL, false, (SpawnHost.Spawn spawn) -> {
      if ("SPAWNER".equals(spawn.reason())) {
        events.onSpawnerSpawn(spawn.entity());
      }
    });
    Subscription creatures = spawns.onCreatureSpawn(ListenerPriority.MONITOR, true,
        (SpawnHost.Spawn spawn) -> events.onSpawn(spawn.entity()));
    Subscription others = spawns.onEntitySpawn(ListenerPriority.MONITOR, true,
        (SpawnHost.EntitySpawn spawn) -> events.onSpawn(spawn.entity()));
    return () -> {
      spawner.cancel();
      creatures.cancel();
      others.cancel();
    };
  }

  /**
   * Loaded chunks of every world, a batch at a time.
   */
  interface ChunkCursor {
    List<ChunkRef> next(int maximum);
  }

  Subscription listen(Events events);

  /**
   * The living, spawnable types stacking applies to by default, in {@link GameEntity#type()} naming.
   */
  Set<String> defaultStackableTypes();

  boolean isLiving(GameEntity entity);

  boolean isDead(GameEntity entity);

  /**
   * True while the entity is in a loaded world and not dead.
   */
  boolean isAlive(GameEntity entity);

  boolean isSameEntity(GameEntity first, GameEntity second);

  boolean isTamedPet(GameEntity entity);

  UUID ownerId(GameEntity entity);

  /**
   * The traits two mobs must share to stack: type, slime size, age group, wool color and profession.
   */
  Object mergeBucket(GameEntity entity);

  /**
   * Hostile and farm state, equipment and potion effects allow the two mobs to merge.
   */
  boolean hasSafeMergeState(GameEntity source, GameEntity target);

  /**
   * Neither type carries variants the stack would lose, and both share an unmodified max health at full health.
   */
  boolean hasSafeHealthState(GameEntity source, GameEntity target);

  /**
   * The max health attribute value, or NaN when the entity has none.
   */
  double maxHealth(GameEntity entity);

  double health(GameEntity entity);

  void setHealth(GameEntity entity, double health);

  /**
   * Sets health clamped to the entity's max health.
   */
  void restoreHealth(GameEntity entity, double health);

  /**
   * Spawns a copy of the source at its location raised by {@code raise} blocks and copies its state, including its
   * custom name when {@code copyName} is set. Null when the spawn was rejected. When copying fails the copy is
   * removed and the failure is rethrown.
   */
  GameEntity spawnCopy(GameEntity source, double raise, boolean copyName);

  void untame(GameEntity entity);

  /**
   * Removes the entity without death, drops or React's removal rules.
   */
  void discard(GameEntity entity);

  /**
   * Plays the pickup animation of {@code collected} flying into {@code collector} for nearby players.
   */
  void sendCollect(GameEntity collected, GameEntity collector);

  boolean hasFlag(GameEntity entity, String flag);

  void setFlag(GameEntity entity, String flag, boolean value);

  String customName(GameEntity entity);

  void setCustomName(GameEntity entity, String name);

  /**
   * The stack name React last applied, kept with the entity across restarts.
   */
  String stackLabel(GameEntity entity);

  void setStackLabel(GameEntity entity, String label);

  void clearStackLabel(GameEntity entity);

  /**
   * Hands the stack count to an overlay plugin that draws it; false when none did.
   */
  boolean showOverlay(GameEntity entity, int count);

  boolean isChunkLoaded(ReactWorld world, int chunkX, int chunkZ);

  /**
   * Entities of a chunk whose entities are loaded, without loading it; null otherwise.
   */
  List<GameEntity> loadedChunkEntities(ReactWorld world, int chunkX, int chunkZ);

  /**
   * A cursor over loaded chunks, or null until the platform's chunk index is ready.
   */
  ChunkCursor openLoadedChunks();
}
