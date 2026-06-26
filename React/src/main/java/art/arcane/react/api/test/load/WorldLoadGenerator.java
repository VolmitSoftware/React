package art.arcane.react.api.test.load;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WorldLoadGenerator {
  private static final int MAX_ENTITIES = 3000;
  private final List<UUID> spawned;
  private World world;
  private Location center;
  private LoadProfile profile;
  private boolean active;

  public WorldLoadGenerator() {
    this.spawned = new ArrayList<UUID>();
    this.active = false;
  }

  public void begin(World world, Location center, LoadProfile profile) {
    if (active || world == null || center == null || profile == null) {
      return;
    }
    this.world = world;
    this.center = center;
    this.profile = profile;
    this.active = true;
    for (int herd = 0; herd < profile.mobHerds(); herd++) {
      Location herdAt = spread(24.0);
      for (int m = 0; m < profile.mobsPerHerd(); m++) {
        spawn(herdAt, EntityType.ZOMBIE);
      }
    }
  }

  public void tick() {
    if (!active || world == null) {
      return;
    }
    if (spawned.size() >= MAX_ENTITIES) {
      pruneDead();
      if (spawned.size() >= MAX_ENTITIES) {
        return;
      }
    }
    for (int i = 0; i < profile.itemFloodPerTick(); i++) {
      dropItem(spread(24.0));
    }
    int falling = Math.max(1, profile.fallingBlocks() / 20);
    for (int i = 0; i < falling; i++) {
      spawnFalling(spread(20.0));
    }
    if (ThreadLocalRandom.current().nextInt(40) == 0) {
      for (int i = 0; i < profile.tntBursts(); i++) {
        spawnTnt(spread(20.0));
      }
    }
  }

  public void end() {
    for (UUID id : spawned) {
      Entity entity = Bukkit.getEntity(id);
      if (entity != null) {
        try {
          entity.remove();
        } catch (Throwable ignored) {
        }
      }
    }
    spawned.clear();
    active = false;
  }

  private void pruneDead() {
    spawned.removeIf(id -> {
      Entity entity = Bukkit.getEntity(id);
      return entity == null || entity.isDead();
    });
  }

  public int spawnedCount() {
    return spawned.size();
  }

  private Location spread(double radius) {
    double x = center.getX() + ThreadLocalRandom.current().nextDouble(-radius, radius);
    double z = center.getZ() + ThreadLocalRandom.current().nextDouble(-radius, radius);
    double y = center.getY() + 1.0;
    return new Location(world, x, y, z);
  }

  private void spawn(Location at, EntityType type) {
    try {
      Entity entity = world.spawnEntity(at, type);
      spawned.add(entity.getUniqueId());
    } catch (Throwable ignored) {
    }
  }

  private void dropItem(Location at) {
    try {
      Entity item = world.dropItem(at, new ItemStack(Material.COBBLESTONE, 16));
      spawned.add(item.getUniqueId());
    } catch (Throwable ignored) {
    }
  }

  private void spawnFalling(Location at) {
    try {
      Entity falling = world.spawnFallingBlock(at, Material.SAND.createBlockData());
      spawned.add(falling.getUniqueId());
    } catch (Throwable ignored) {
    }
  }

  private void spawnTnt(Location at) {
    try {
      double highY = Math.min(world.getMaxHeight() - 2.0, at.getY() + 80.0);
      Location high = new Location(world, at.getX(), highY, at.getZ());
      Entity tnt = world.spawnEntity(high, EntityType.TNT);
      spawned.add(tnt.getUniqueId());
    } catch (Throwable ignored) {
    }
  }
}
