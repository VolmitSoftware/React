package art.arcane.react.content.feature;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HostListeners;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.Subscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class FakeSpawnHost implements SpawnHost {
  public final HostListeners<Spawn> creatures = new HostListeners<>();
  public final HostListeners<EntitySpawn> entities = new HostListeners<>();
  public final HostListeners<Breed> breeds = new HostListeners<>();
  public final HostListeners<ItemDrop> drops = new HostListeners<>();
  public final Map<Long, Integer> chunkCounts = new HashMap<>();
  public final List<GameLocation> backoffs = new ArrayList<>();
  public final Set<Long> darkCells = new HashSet<>();
  public int counts;
  public int scans;

  @Override
  public Subscription onCreatureSpawn(ListenerPriority priority, boolean ignoreCancelled, Consumer<Spawn> listener) {
    return creatures.subscribe(priority, ignoreCancelled, listener);
  }

  @Override
  public Subscription onEntitySpawn(ListenerPriority priority, boolean ignoreCancelled, Consumer<EntitySpawn> listener) {
    return entities.subscribe(priority, ignoreCancelled, listener);
  }

  @Override
  public Subscription onBreed(ListenerPriority priority, boolean ignoreCancelled, Consumer<Breed> listener) {
    return breeds.subscribe(priority, ignoreCancelled, listener);
  }

  @Override
  public Subscription onPlayerDropItem(ListenerPriority priority, boolean ignoreCancelled, Consumer<ItemDrop> listener) {
    return drops.subscribe(priority, ignoreCancelled, listener);
  }

  @Override
  public int chunkEntityCount(UUID worldId, int chunkX, int chunkZ, boolean countItems) {
    counts++;
    Integer count = chunkCounts.get(key(chunkX, chunkZ));
    return count == null ? -1 : count;
  }

  @Override
  public boolean spawnProtected(String type, GameLocation location, String reason) {
    return false;
  }

  @Override
  public void backoffSpawners(GameLocation location, int delayTicks) {
    backoffs.add(location);
  }

  @Override
  public void forEachDarkAir(GameLocation center, int horizontalRadius, int verticalRadius, int maxLight, CellVisitor visitor) {
    scans++;
    for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
      for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
        for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
          int x = center.blockX() + dx;
          int y = center.blockY() + dy;
          int z = center.blockZ() + dz;
          if (darkCells.contains(cell(x, y, z))) {
            visitor.visit(x, y, z);
          }
        }
      }
    }
  }

  public static long key(int chunkX, int chunkZ) {
    return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
  }

  public static long cell(int x, int y, int z) {
    return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
  }

  public static TestSpawn spawn(GameLocation location, String reason, boolean monster) {
    return new TestSpawn(location, reason, monster, false);
  }

  public static final class TestSpawn implements Spawn {
    private final GameLocation location;
    private final String reason;
    private final boolean monster;
    private final boolean named;
    private boolean cancelled;

    public TestSpawn(GameLocation location, String reason, boolean monster, boolean named) {
      this.location = location;
      this.reason = reason;
      this.monster = monster;
      this.named = named;
    }

    @Override
    public GameEntity entity() {
      return null;
    }

    @Override
    public GameLocation location() {
      return location;
    }

    @Override
    public String type() {
      return monster ? "ZOMBIE" : "COW";
    }

    @Override
    public String reason() {
      return reason;
    }

    @Override
    public boolean isMonster() {
      return monster;
    }

    @Override
    public boolean hasCustomName() {
      return named;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }
}
