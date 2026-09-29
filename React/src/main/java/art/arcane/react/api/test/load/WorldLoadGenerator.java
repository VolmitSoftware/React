package art.arcane.react.api.test.load;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WorldLoadGenerator {
  private static final int MAX_ENTITIES = 3000;
  private static final int MAX_HOPPER_NETWORKS = 64;
  private static final int HOPPER_NETWORKS_PER_ROW = 8;
  private static final int HOPPER_NETWORK_SPACING = 4;
  private static final int HOPPER_NETWORK_OFFSET_X = 32;
  private static final int HOPPER_NETWORK_OFFSET_Y = 8;
  private static final int ITEMS_PER_HOPPER = 16;
  private static final int[][] HOPPER_RING = {{0, 0}, {1, 0}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {0, 1}};
  private static final BlockFace[] HOPPER_RING_FACING = {
      BlockFace.EAST, BlockFace.EAST, BlockFace.SOUTH, BlockFace.SOUTH,
      BlockFace.WEST, BlockFace.WEST, BlockFace.NORTH, BlockFace.NORTH
  };
  private final List<UUID> spawned;
  private final List<BlockState> replacedBlocks;
  private World world;
  private Location center;
  private LoadProfile profile;
  private boolean active;

  public WorldLoadGenerator() {
    this.spawned = new ArrayList<UUID>();
    this.replacedBlocks = new ArrayList<BlockState>();
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
    int networks = Math.min(MAX_HOPPER_NETWORKS, Math.max(0, profile.hopperNetworks()));
    for (int network = 0; network < networks; network++) {
      buildHopperNetwork(network);
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
    restoreReplacedBlocks();
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

  private void buildHopperNetwork(int network) {
    int originX = center.getBlockX() + HOPPER_NETWORK_OFFSET_X + (network % HOPPER_NETWORKS_PER_ROW) * HOPPER_NETWORK_SPACING;
    int originY = Math.min(world.getMaxHeight() - 2, center.getBlockY() + HOPPER_NETWORK_OFFSET_Y);
    int originZ = center.getBlockZ() + (network / HOPPER_NETWORKS_PER_ROW) * HOPPER_NETWORK_SPACING;
    for (int i = 0; i < HOPPER_RING.length; i++) {
      placeHopper(world.getBlockAt(originX + HOPPER_RING[i][0], originY, originZ + HOPPER_RING[i][1]), HOPPER_RING_FACING[i]);
    }
  }

  private void placeHopper(Block block, BlockFace facing) {
    try {
      BlockState original = block.getState();
      Directional hopperData = (Directional) Material.HOPPER.createBlockData();
      hopperData.setFacing(facing);
      block.setBlockData(hopperData, false);
      replacedBlocks.add(original);
      if (block.getState() instanceof Container hopper) {
        hopper.getInventory().addItem(new ItemStack(Material.COBBLESTONE, ITEMS_PER_HOPPER));
      }
    } catch (Throwable ignored) {
    }
  }

  private void restoreReplacedBlocks() {
    for (int i = replacedBlocks.size() - 1; i >= 0; i--) {
      BlockState original = replacedBlocks.get(i);
      try {
        if (original.getBlock().getState() instanceof Container hopper) {
          hopper.getInventory().clear();
        }
        original.update(true, false);
      } catch (Throwable ignored) {
      }
    }
    replacedBlocks.clear();
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
