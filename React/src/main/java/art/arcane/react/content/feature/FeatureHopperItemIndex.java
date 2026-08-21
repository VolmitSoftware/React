package art.arcane.react.content.feature;

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.core.controller.HopperItemIndex;
import art.arcane.react.core.controller.HopperPositionIndex;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ConfigDescription("Maintains spatial indices of dropped items and hopper positions. Used by TweakHopperIndex to short-circuit vanilla hopper AABB scans.")
public class FeatureHopperItemIndex extends ReactFeature implements Listener {
    public static final String ID = "hopper-item-index";

    @ConfigDoc(value = "Interval in milliseconds between reconciliation sweeps that correct index drift.", impact = "Lower values catch drift faster at slightly higher overhead. Default matches 40 server ticks at 20 TPS.")
    private int reconcileIntervalMs = 2000;

    private transient HopperItemIndex itemIndex;
    private transient HopperPositionIndex positionIndex;

    public FeatureHopperItemIndex() {
        super(ID);
    }

    @Override
    public void onActivate() {
        itemIndex = new HopperItemIndex();
        positionIndex = new HopperPositionIndex();
        if (!J.isFoliaThreading()) {
            seedFromLoadedChunks();
        }
    }

    @Override
    public void onDeactivate() {
        if (itemIndex != null) {
            itemIndex.clear();
            itemIndex = null;
        }
        if (positionIndex != null) {
            positionIndex.clear();
            positionIndex = null;
        }
    }

    @Override
    public int getTickInterval() {
        return reconcileIntervalMs;
    }

    @Override
    public void onTick() {
        J.s(this::reconcileItems);
        if (!J.isFoliaThreading()) {
            J.s(this::reconcileHoppers);
        }
    }

    public HopperItemIndex getItemIndex() {
        return itemIndex;
    }

    public HopperPositionIndex getPositionIndex() {
        return positionIndex;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ItemSpawnEvent event) {
        if (itemIndex == null) {
            return;
        }
        Item item = event.getEntity();
        Location loc = item.getLocation();
        if (loc.getWorld() == null) {
            return;
        }
        itemIndex.addItem(loc.getWorld().getUID(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4, item.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ItemDespawnEvent event) {
        if (itemIndex == null) {
            return;
        }
        itemIndex.removeItem(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityPickupItemEvent event) {
        if (itemIndex == null) {
            return;
        }
        itemIndex.removeItem(event.getItem().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(InventoryPickupItemEvent event) {
        if (itemIndex == null) {
            return;
        }
        itemIndex.removeItem(event.getItem().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockPlaceEvent event) {
        if (positionIndex == null) {
            return;
        }
        Block block = event.getBlock();
        if (block.getType() != Material.HOPPER) {
            return;
        }
        UUID worldId = block.getWorld().getUID();
        positionIndex.addHopper(worldId, block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockBreakEvent event) {
        if (positionIndex == null) {
            return;
        }
        Block block = event.getBlock();
        if (block.getType() != Material.HOPPER) {
            return;
        }
        UUID worldId = block.getWorld().getUID();
        positionIndex.removeHopper(worldId, block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockExplodeEvent event) {
        if (positionIndex == null) {
            return;
        }
        UUID worldId = event.getBlock().getWorld().getUID();
        for (Block block : event.blockList()) {
            if (block.getType() == Material.HOPPER) {
                positionIndex.removeHopper(worldId, block.getX(), block.getY(), block.getZ());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityExplodeEvent event) {
        if (positionIndex == null) {
            return;
        }
        UUID worldId = event.getLocation().getWorld().getUID();
        for (Block block : event.blockList()) {
            if (block.getType() == Material.HOPPER) {
                positionIndex.removeHopper(worldId, block.getX(), block.getY(), block.getZ());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockPistonExtendEvent event) {
        if (positionIndex == null) {
            return;
        }
        BlockFace direction = event.getDirection();
        UUID worldId = event.getBlock().getWorld().getUID();
        for (Block moved : event.getBlocks()) {
            if (moved.getType() == Material.HOPPER) {
                positionIndex.removeHopper(worldId, moved.getX(), moved.getY(), moved.getZ());
                positionIndex.addHopper(worldId,
                    moved.getX() + direction.getModX(),
                    moved.getY() + direction.getModY(),
                    moved.getZ() + direction.getModZ());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockPistonRetractEvent event) {
        if (positionIndex == null) {
            return;
        }
        BlockFace direction = event.getDirection();
        UUID worldId = event.getBlock().getWorld().getUID();
        for (Block moved : event.getBlocks()) {
            if (moved.getType() == Material.HOPPER) {
                positionIndex.removeHopper(worldId, moved.getX(), moved.getY(), moved.getZ());
                positionIndex.addHopper(worldId,
                    moved.getX() - direction.getModX(),
                    moved.getY() - direction.getModY(),
                    moved.getZ() - direction.getModZ());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ChunkLoadEvent event) {
        if (itemIndex == null || positionIndex == null) {
            return;
        }
        Chunk chunk = event.getChunk();
        seedChunk(chunk);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        UUID worldId = chunk.getWorld().getUID();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        if (itemIndex != null) {
            itemIndex.removeChunk(worldId, cx, cz);
        }
        if (positionIndex != null) {
            positionIndex.removeChunk(worldId, cx, cz);
        }
    }

    private void seedFromLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                seedChunk(chunk);
            }
        }
    }

    private void seedChunk(Chunk chunk) {
        UUID worldId = chunk.getWorld().getUID();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        if (itemIndex != null) {
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Item item && item.isValid()) {
                    itemIndex.addItem(worldId, cx, cz, item.getUniqueId());
                }
            }
        }
        if (positionIndex != null) {
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Hopper) {
                    Location loc = state.getLocation();
                    positionIndex.addHopper(worldId, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                }
            }
        }
    }

    private void reconcileItems() {
        if (itemIndex == null) {
            return;
        }
        for (UUID itemId : itemIndex.allItemIds()) {
            Entity entity = Bukkit.getEntity(itemId);
            if (entity == null || !entity.isValid() || entity.isDead()) {
                itemIndex.removeItem(itemId);
                continue;
            }
            Location loc = entity.getLocation();
            if (loc.getWorld() == null) {
                continue;
            }
            itemIndex.addItem(loc.getWorld().getUID(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4, itemId);
        }
    }

    private void reconcileHoppers() {
        if (positionIndex == null) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            UUID worldId = world.getUID();
            List<Long> toRemove = new ArrayList<>();
            positionIndex.forEachHopperInWorld(worldId, packed -> {
                int x = HopperPositionIndex.unpackX(packed);
                int y = HopperPositionIndex.unpackY(packed);
                int z = HopperPositionIndex.unpackZ(packed);
                if (world.isChunkLoaded(x >> 4, z >> 4) && world.getBlockAt(x, y, z).getType() != Material.HOPPER) {
                    toRemove.add(packed);
                }
            });
            for (long packed : toRemove) {
                int x = HopperPositionIndex.unpackX(packed);
                int y = HopperPositionIndex.unpackY(packed);
                int z = HopperPositionIndex.unpackZ(packed);
                positionIndex.removeHopper(worldId, x, y, z);
            }
        }
    }
}
