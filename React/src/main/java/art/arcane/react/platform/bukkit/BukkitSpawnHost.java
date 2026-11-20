package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.api.protect.internal.ProtectionGuards;
import art.arcane.react.api.protect.internal.ProtectionInstaller;
import art.arcane.react.core.controller.PdcWriteBatcher;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.UUID;
import java.util.function.Consumer;

public class BukkitSpawnHost implements SpawnHost {
  @Override
  public Subscription onCreatureSpawn(ListenerPriority priority, boolean ignoreCancelled, Consumer<Spawn> listener) {
    return BukkitEvents.subscribe(CreatureSpawnEvent.class, priority, ignoreCancelled, event -> listener.accept(spawn(event)));
  }

  @Override
  public Subscription onEntitySpawn(ListenerPriority priority, boolean ignoreCancelled, Consumer<EntitySpawn> listener) {
    return BukkitEvents.subscribe(EntitySpawnEvent.class, priority, ignoreCancelled, event -> {
      EntitySpawn spawn = entitySpawn(event);
      if (spawn != null) {
        listener.accept(spawn);
      }
    });
  }

  @Override
  public Subscription onBreed(ListenerPriority priority, boolean ignoreCancelled, Consumer<Breed> listener) {
    return BukkitEvents.subscribe(EntityBreedEvent.class, priority, ignoreCancelled, event -> listener.accept(breed(event)));
  }

  @Override
  public Subscription onPlayerDropItem(ListenerPriority priority, boolean ignoreCancelled, Consumer<ItemDrop> listener) {
    return BukkitEvents.subscribe(PlayerDropItemEvent.class, priority, ignoreCancelled, event -> listener.accept(drop(event)));
  }

  public static Spawn spawn(CreatureSpawnEvent event) {
    return new BukkitSpawn(event);
  }

  /**
   * The host event for a Bukkit entity spawn, or null for creature spawns, which {@link #spawn} covers.
   */
  public static EntitySpawn entitySpawn(EntitySpawnEvent event) {
    return event instanceof CreatureSpawnEvent ? null : new BukkitEntitySpawn(event);
  }

  public static Breed breed(EntityBreedEvent event) {
    return new BukkitBreed(event);
  }

  public static ItemDrop drop(PlayerDropItemEvent event) {
    return new BukkitItemDrop(event);
  }

  @Override
  public int chunkEntityCount(UUID worldId, int chunkX, int chunkZ, boolean countItems) {
    return chunkEntityCount(Bukkit.getWorld(worldId), chunkX, chunkZ, countItems);
  }

  static int chunkEntityCount(World world, int chunkX, int chunkZ, boolean countItems) {
    if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
      return -1;
    }
    Chunk chunk = world.getChunkAt(chunkX, chunkZ, false);
    if (!chunk.isEntitiesLoaded()) {
      return -1;
    }
    int entityCount = 0;
    for (Entity entity : chunk.getEntities()) {
      if (!(entity instanceof Item) || countItems) {
        entityCount++;
      }
    }
    return entityCount;
  }

  @Override
  public boolean spawnProtected(String type, GameLocation location, String reason) {
    if (ProtectionInstaller.binding() == null) {
      return false;
    }
    World world = location == null ? null : Bukkit.getWorld(location.worldId());
    return spawnProtected(type, world, reason);
  }

  static boolean spawnProtected(String type, World world, String reason) {
    EntityType entityType;
    try {
      entityType = EntityType.valueOf(type);
    } catch (IllegalArgumentException | NullPointerException unknown) {
      return false;
    }
    CreatureSpawnEvent.SpawnReason spawnReason = reason == null ? null : CreatureSpawnEvent.SpawnReason.valueOf(reason);
    return ProtectionGuards.spawnProtected(entityType, world == null ? "" : world.getName(), spawnReason);
  }

  @Override
  public void backoffSpawners(GameLocation location, int delayTicks) {
    World world = location == null ? null : Bukkit.getWorld(location.worldId());
    if (world == null) {
      return;
    }

    // Spawners place mobs within 4 blocks horizontally and 1 vertically of themselves.
    int blockX = location.blockX();
    int blockY = location.blockY();
    int blockZ = location.blockZ();
    for (int y = -1; y <= 1; y++) {
      for (int x = -4; x <= 4; x++) {
        for (int z = -4; z <= 4; z++) {
          int worldX = blockX + x;
          int worldZ = blockZ + z;
          if (!world.isChunkLoaded(worldX >> 4, worldZ >> 4)) {
            continue;
          }

          Block block = world.getBlockAt(worldX, blockY + y, worldZ);
          if (block.getType() != Material.SPAWNER) {
            continue;
          }

          BlockState state = block.getState();
          if (state instanceof CreatureSpawner spawner) {
            spawner.setDelay(Math.max(spawner.getDelay(), delayTicks));
            PdcWriteBatcher batcher = React.controller(PdcWriteBatcher.class);
            if (batcher != null) {
              batcher.enqueue(spawner, false, false);
            } else {
              spawner.update(false, false);
            }
          }
        }
      }
    }
  }

  @Override
  public void forEachDarkAir(GameLocation center, int horizontalRadius, int verticalRadius, int maxLight, CellVisitor visitor) {
    World world = center == null ? null : Bukkit.getWorld(center.worldId());
    if (world == null) {
      return;
    }
    int centerX = center.blockX();
    int centerY = center.blockY();
    int centerZ = center.blockZ();
    for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
      int y = centerY + dy;
      if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
        continue;
      }
      for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
        for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
          int x = centerX + dx;
          int z = centerZ + dz;
          if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            continue;
          }

          Location cell = new Location(world, x, y, z);
          if (!J.isOwnedByCurrentRegion(cell)) {
            continue;
          }

          Block block = world.getBlockAt(x, y, z);
          if (block.getType() != Material.AIR && block.getType() != Material.CAVE_AIR && block.getType() != Material.VOID_AIR) {
            continue;
          }

          if (block.getLightLevel() <= maxLight) {
            visitor.visit(x, y, z);
          }
        }
      }
    }
  }

  private static GameLocation at(Location location) {
    return BukkitGameEntity.location(location);
  }

  private record BukkitSpawn(CreatureSpawnEvent event) implements Spawn {
    @Override
    public GameEntity entity() {
      return new BukkitGameEntity(event.getEntity());
    }

    @Override
    public GameLocation location() {
      return at(event.getLocation());
    }

    @Override
    public String type() {
      return event.getEntityType().name();
    }

    @Override
    public String reason() {
      return event.getSpawnReason().name();
    }

    @Override
    public boolean isMonster() {
      return event.getEntity() instanceof Monster;
    }

    @Override
    public boolean hasCustomName() {
      String name = event.getEntity().getCustomName();
      return name != null && !name.isBlank();
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }
  }

  private record BukkitEntitySpawn(EntitySpawnEvent event) implements EntitySpawn {
    @Override
    public GameLocation location() {
      return at(event.getEntity().getLocation());
    }

    @Override
    public String type() {
      return event.getEntity().getType().name();
    }

    @Override
    public boolean isItem() {
      return event.getEntity() instanceof Item;
    }

    @Override
    public boolean precedesAdd() {
      return event instanceof SpawnerSpawnEvent || event instanceof TrialSpawnerSpawnEvent;
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }
  }

  private record BukkitBreed(EntityBreedEvent event) implements Breed {
    @Override
    public GameLocation location() {
      return at(event.getEntity().getLocation());
    }

    @Override
    public String type() {
      return event.getEntity().getType().name();
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }
  }

  private record BukkitItemDrop(PlayerDropItemEvent event) implements ItemDrop {
    @Override
    public GameLocation location() {
      return at(event.getPlayer().getLocation());
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }
  }
}
