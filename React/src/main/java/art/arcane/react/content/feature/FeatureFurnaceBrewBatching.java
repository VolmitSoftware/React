/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.PressureGate;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.nms.BrewingTickHook;
import art.arcane.react.nms.BrewingTickResult;
import art.arcane.react.nms.FurnaceTickHook;
import art.arcane.react.nms.FurnaceTickResult;
import art.arcane.react.nms.NmsBridge;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Furnace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Configuration for Furnace/Brew Batching feature. Tracks active furnaces and brewing stands per chunk and measures the number of intermediate ticks that could be skipped between fuel-out / cook-complete events. Reports projected skip volume as a runtime metric; bypasses any block within bypassRadius of a player so cosmetic flame/smoke remains lively near players.")
public class FeatureFurnaceBrewBatching extends ReactFeature implements Listener {
  public static final String ID = "furnace-brew-batching";
  private static final int FURNACE_BASE_COOK_TICKS = 200;
  private static final int BLAST_FURNACE_BASE_COOK_TICKS = 100;
  private static final int SMOKER_BASE_COOK_TICKS = 100;
  private static final int BREW_TICKS = 400;
  private static final int BREW_FUEL_BURN_TICKS = 20;

  @ConfigDoc(value ="Main evaluation interval for furnace/brew batching in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @ConfigDoc(value ="Block radius around each player where furnace/brew batching is bypassed.", impact = "Higher values keep more nearby furnaces ticking normally; lower values let batching reach closer to players.")
  private int bypassRadius = 16;
  @ConfigDoc(value ="Incident score (0-100) required before furnace/brew batching engages.", impact = "Lower values engage during milder incidents; higher values wait for severe incidents.")
  private double engageIncidentScore = 55;
  @ConfigDoc(value ="Average tick milliseconds required before furnace/brew batching engages.", impact = "Lower values engage earlier; higher values reserve batching for heavier load.")
  private double engageTickTimeMs = 55;
  @ConfigDoc(value ="Average tick milliseconds the server must stay below before batching releases.", impact = "Lower values hold batching longer for stability; higher values release sooner.")
  private double releaseTickTimeMs = 42;
  @ConfigDoc(value ="Sustained pressure duration required before engaging (milliseconds).", impact = "Higher values ignore short spikes; lower values engage faster.")
  private long sustainEngageMs = 6000;
  @ConfigDoc(value ="Sustained recovery duration required before releasing (milliseconds).", impact = "Higher values avoid flapping between states; lower values release sooner.")
  private long sustainReleaseMs = 30_000;
  @ConfigDoc(value ="Maximum tracked block-entities allowed by furnace/brew batching.", impact = "Higher values track larger farms; lower values cap memory usage at the cost of accuracy.")
  private int maxTrackedEntries = 8192;
  @ConfigDoc(value ="Maximum chunks reseeded per maintenance tick.", impact = "Higher values keep the index fresher but raise per-tick overhead; lower values smooth CPU but lag updates.")
  private int reseedChunksPerTick = 32;

  private transient final Map<Long, ChunkIndex> chunkIndexByKey = new ConcurrentHashMap<>();
  private transient final AtomicLong activeBlockCount = new AtomicLong();
  private transient final AtomicLong projectedSkippableTicks = new AtomicLong();
  private transient final AtomicLong pendingProjectedSkippableTicks = new AtomicLong();
  private transient final AtomicLong pendingActiveBlocks = new AtomicLong();
  @Getter
  private transient volatile long lastTotalActive;
  @Getter
  private transient volatile long lastTotalProjectedSkippableTicks;
  @Getter
  private transient volatile long lastBypassedByPlayer;
  private transient final PressureGate gate = new PressureGate();
  @Getter
  private transient volatile boolean bridgeActive;
  private transient final AtomicLong skippedFurnaceTicks = new AtomicLong();
  private transient final AtomicLong skippedBrewingTicks = new AtomicLong();
  private transient final Long2IntOpenHashMap furnaceSkipDebt = new Long2IntOpenHashMap();
  private transient final Long2IntOpenHashMap brewingSkipDebt = new Long2IntOpenHashMap();

  public FeatureFurnaceBrewBatching() {
    super(ID);
  }

  @Override
  public void onActivate() {
    chunkIndexByKey.clear();
    activeBlockCount.set(0);
    projectedSkippableTicks.set(0);
    pendingProjectedSkippableTicks.set(0);
    pendingActiveBlocks.set(0);
    skippedFurnaceTicks.set(0);
    skippedBrewingTicks.set(0);
    synchronized (furnaceSkipDebt) {
      furnaceSkipDebt.clear();
    }
    synchronized (brewingSkipDebt) {
      brewingSkipDebt.clear();
    }
    lastTotalActive = 0;
    lastTotalProjectedSkippableTicks = 0;
    lastBypassedByPlayer = 0;
    gate.reset();
    seedFromLoadedChunks();
    installBridgeHooks();
  }

  @Override
  public void onDeactivate() {
    chunkIndexByKey.clear();
    activeBlockCount.set(0);
    projectedSkippableTicks.set(0);
    pendingProjectedSkippableTicks.set(0);
    pendingActiveBlocks.set(0);
    gate.reset();
    synchronized (furnaceSkipDebt) {
      furnaceSkipDebt.clear();
    }
    synchronized (brewingSkipDebt) {
      brewingSkipDebt.clear();
    }
    uninstallBridgeHooks();
  }

  public boolean isMeasurementOnly() {
    return !bridgeActive;
  }

  public long readAndResetSkippedFurnaceTicks() {
    return skippedFurnaceTicks.getAndSet(0L);
  }

  public long readAndResetSkippedBrewingTicks() {
    return skippedBrewingTicks.getAndSet(0L);
  }

  private void installBridgeHooks() {
    NmsBridge bridge = NmsBridges.get();
    if (bridge == null) {
      bridgeActive = false;
      return;
    }
    FurnaceTickHook furnace = (World world, int x, int y, int z) -> {
      long key = blockKey(x, y, z);
      boolean runVanilla = !gate.isEngaged()
          || React.hasNearbyPlayer(new Location(world, x + 0.5D, y + 0.5D, z + 0.5D), bypassRadius);
      if (runVanilla) {
        int debt;
        synchronized (furnaceSkipDebt) {
          debt = furnaceSkipDebt.remove(key);
        }
        if (debt > 0) {
          return FurnaceTickResult.runAndAdvance(debt);
        }
        return FurnaceTickResult.RUN_VANILLA;
      }
      synchronized (furnaceSkipDebt) {
        furnaceSkipDebt.addTo(key, 1);
      }
      skippedFurnaceTicks.incrementAndGet();
      return FurnaceTickResult.SKIP;
    };
    BrewingTickHook brewing = (World world, int x, int y, int z) -> {
      long key = blockKey(x, y, z);
      boolean runVanilla = !gate.isEngaged()
          || React.hasNearbyPlayer(new Location(world, x + 0.5D, y + 0.5D, z + 0.5D), bypassRadius);
      if (runVanilla) {
        int debt;
        synchronized (brewingSkipDebt) {
          debt = brewingSkipDebt.remove(key);
        }
        if (debt > 0) {
          return BrewingTickResult.runAndAdvance(debt);
        }
        return BrewingTickResult.RUN_VANILLA;
      }
      synchronized (brewingSkipDebt) {
        brewingSkipDebt.addTo(key, 1);
      }
      skippedBrewingTicks.incrementAndGet();
      return BrewingTickResult.SKIP;
    };
    boolean furnaceOk = bridge.installFurnaceTickHook(furnace);
    boolean brewingOk = bridge.installBrewingTickHook(brewing);
    bridgeActive = furnaceOk || brewingOk;
  }

  private void uninstallBridgeHooks() {
    NmsBridge bridge = NmsBridges.get();
    if (bridge != null) {
      bridge.uninstallFurnaceTickHook();
      bridge.uninstallBrewingTickHook();
    }
    bridgeActive = false;
  }

  @Override
  public int getTickInterval() {
    return Math.max(250, tickIntervalMS);
  }

  @Override
  public void onTick() {
    updateEngagement();
    if (!gate.isEngaged()) {
      lastTotalActive = 0;
      lastTotalProjectedSkippableTicks = 0;
      lastBypassedByPlayer = 0;
      return;
    }

    measure();
  }

  private void updateEngagement() {
    double tickMs = sample(SamplerTickTime.ID);
    double incident = sample(SamplerIncidentScore.ID);
    boolean pressure = tickMs >= engageTickTimeMs || incident >= engageIncidentScore;
    boolean calm = tickMs <= releaseTickTimeMs && incident < engageIncidentScore;
    long now = System.currentTimeMillis();
    boolean wasEngaged = gate.isEngaged();
    boolean nowEngaged = gate.update(now, pressure, calm, sustainEngageMs, sustainReleaseMs);

    if (!wasEngaged && nowEngaged) {
      React.verbose("Furnace/brew batching engaged.");
    } else if (wasEngaged && !nowEngaged) {
      React.verbose("Furnace/brew batching released.");
    }
  }

  private void measure() {
    long completedActive = pendingActiveBlocks.get();
    long completedSkippable = pendingProjectedSkippableTicks.get();
    lastTotalActive = completedActive;
    lastTotalProjectedSkippableTicks = completedSkippable;
    activeBlockCount.set(completedActive);
    projectedSkippableTicks.set(completedSkippable);

    pendingActiveBlocks.set(0);
    pendingProjectedSkippableTicks.set(0);

    long bypassed = 0;
    for (Map.Entry<Long, ChunkIndex> entry : chunkIndexByKey.entrySet()) {
      ChunkIndex index = entry.getValue();
      if (index == null) {
        continue;
      }

      Map<UUID, List<TrackedBlock>> measurableByWorld = new HashMap<>();
      for (TrackedBlock block : index.snapshot()) {
        if (block == null) {
          continue;
        }

        World blockWorld = Bukkit.getWorld(block.worldId);
        if (blockWorld == null) {
          index.remove(block.x, block.y, block.z);
          continue;
        }

        Location location = new Location(blockWorld, block.x + 0.5D, block.y + 0.5D, block.z + 0.5D);
        if (React.hasNearbyPlayer(location, bypassRadius)) {
          bypassed++;
          continue;
        }

        measurableByWorld.computeIfAbsent(block.worldId, k -> new ArrayList<>()).add(block);
      }

      for (Map.Entry<UUID, List<TrackedBlock>> worldEntry : measurableByWorld.entrySet()) {
        World world = Bukkit.getWorld(worldEntry.getKey());
        if (world == null) {
          continue;
        }

        List<TrackedBlock> measurable = worldEntry.getValue();
        TrackedBlock anchor = measurable.get(0);
        dispatchChunkMeasurement(world, anchor.x >> 4, anchor.z >> 4, measurable);
      }
    }

    lastBypassedByPlayer = bypassed;
  }

  private void dispatchChunkMeasurement(World world, int chunkX, int chunkZ, List<TrackedBlock> blocks) {
    Runnable task = () -> {
      long active = 0;
      long skippable = 0;
      for (TrackedBlock block : blocks) {
        long projected = projectSkippableNow(world, block);
        if (projected <= 0) {
          continue;
        }

        active++;
        skippable += projected;
      }

      if (active > 0) {
        pendingActiveBlocks.addAndGet(active);
      }

      if (skippable > 0) {
        pendingProjectedSkippableTicks.addAndGet(skippable);
      }
    };

    if (J.isFoliaThreading()) {
      J.runChunk(world, chunkX, chunkZ, task);
      return;
    }

    task.run();
  }

  private long projectSkippableNow(World world, TrackedBlock block) {
    Chunk chunk = chunkOrNull(world, block.x >> 4, block.z >> 4);
    if (chunk == null) {
      return 0;
    }

    BlockState state = chunk.getBlock(block.x & 15, block.y, block.z & 15).getState();
    return switch (block.kind) {
      case FURNACE -> projectFurnaceSkippable(state);
      case BREWING_STAND -> projectBrewingSkippable(state);
    };
  }

  private long projectFurnaceSkippable(BlockState state) {
    if (!(state instanceof Furnace furnace)) {
      return 0;
    }

    short burnTime = furnace.getBurnTime();
    short cookTime = furnace.getCookTime();
    int baseCook = baseCookTicks(furnace);
    int cookTotal = effectiveCookTotal(furnace, baseCook);
    int cookRemaining = Math.max(0, cookTotal - cookTime);
    FurnaceInventory inv = furnace.getSnapshotInventory();
    boolean hasSmeltable = !isEmpty(inv.getSmelting());
    boolean hasFuel = burnTime > 0 || !isEmpty(inv.getFuel());

    if (!hasFuel || !hasSmeltable) {
      return 0;
    }

    int next = Math.min(burnTime > 0 ? burnTime : Integer.MAX_VALUE, cookRemaining > 0 ? cookRemaining : Integer.MAX_VALUE);
    if (next == Integer.MAX_VALUE) {
      return 0;
    }

    return Math.max(0, next - 1);
  }

  private long projectBrewingSkippable(BlockState state) {
    if (!(state instanceof BrewingStand stand)) {
      return 0;
    }

    int fuel = stand.getFuelLevel();
    int brewTime = stand.getBrewingTime();
    BrewerInventory inv = stand.getSnapshotInventory();
    boolean hasIngredient = !isEmpty(inv.getIngredient());
    boolean hasBottle = !isEmpty(inv.getItem(0)) || !isEmpty(inv.getItem(1)) || !isEmpty(inv.getItem(2));

    if (!hasIngredient || !hasBottle) {
      return 0;
    }

    if (brewTime > 0) {
      return Math.max(0, brewTime - 1);
    }

    if (fuel <= 0) {
      return 0;
    }

    return Math.max(0, BREW_TICKS - 1);
  }

  private int baseCookTicks(Furnace furnace) {
    return switch (furnace.getType().name()) {
      case "BLAST_FURNACE" -> BLAST_FURNACE_BASE_COOK_TICKS;
      case "SMOKER" -> SMOKER_BASE_COOK_TICKS;
      default -> FURNACE_BASE_COOK_TICKS;
    };
  }

  private int effectiveCookTotal(Furnace furnace, int baseCook) {
    int cookTimeTotal = furnace.getCookTimeTotal();
    return cookTimeTotal > 0 ? cookTimeTotal : baseCook;
  }

  private boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
  }

  private Chunk chunkOrNull(World world, int cx, int cz) {
    if (!world.isChunkLoaded(cx, cz)) {
      return null;
    }

    return world.getChunkAt(cx, cz);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkLoadEvent event) {
    Chunk chunk = event.getChunk();
    seedChunk(chunk);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkUnloadEvent event) {
    Chunk chunk = event.getChunk();
    long key = chunkKey(chunk.getX(), chunk.getZ());
    chunkIndexByKey.remove(key);
    synchronized (furnaceSkipDebt) {
      evictChunkDebt(furnaceSkipDebt, chunk.getX(), chunk.getZ());
    }
    synchronized (brewingSkipDebt) {
      evictChunkDebt(brewingSkipDebt, chunk.getX(), chunk.getZ());
    }
  }

  private static void evictChunkDebt(Long2IntOpenHashMap debt, int cx, int cz) {
    LongIterator iterator = debt.keySet().iterator();
    while (iterator.hasNext()) {
      long blockKey = iterator.nextLong();
      if (blockKeyChunkX(blockKey) == cx && blockKeyChunkZ(blockKey) == cz) {
        iterator.remove();
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent event) {
    TrackedKind kind = classify(event.getBlock().getType().name());
    if (kind == null) {
      return;
    }

    Location loc = event.getBlock().getLocation();
    add(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), kind);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent event) {
    Location loc = event.getBlock().getLocation();
    long key = chunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    ChunkIndex index = chunkIndexByKey.get(key);
    if (index != null) {
      index.remove(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    long blockKey = blockKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    synchronized (furnaceSkipDebt) {
      furnaceSkipDebt.remove(blockKey);
    }
    synchronized (brewingSkipDebt) {
      brewingSkipDebt.remove(blockKey);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(InventoryOpenEvent event) {
    touchHolder(event.getInventory());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(InventoryMoveItemEvent event) {
    touchHolder(event.getDestination());
    touchHolder(event.getSource());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(FurnaceBurnEvent event) {
    Location loc = event.getBlock().getLocation();
    add(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), TrackedKind.FURNACE);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(FurnaceSmeltEvent event) {
    Location loc = event.getBlock().getLocation();
    add(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), TrackedKind.FURNACE);
  }

  private void touchHolder(Inventory inventory) {
    if (inventory == null) {
      return;
    }

    Location loc = inventory.getLocation();
    if (loc == null) {
      return;
    }

    TrackedKind kind = classify(loc.getBlock().getType().name());
    if (kind == null) {
      return;
    }

    add(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), kind);
  }

  private void add(World world, int x, int y, int z, TrackedKind kind) {
    if (world == null) {
      return;
    }

    if (activeBlockCount.get() >= maxTrackedEntries) {
      return;
    }

    long key = chunkKey(x >> 4, z >> 4);
    ChunkIndex index = chunkIndexByKey.computeIfAbsent(key, k -> new ChunkIndex());
    if (index.add(world.getUID(), x, y, z, kind)) {
      activeBlockCount.incrementAndGet();
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
    if (chunk == null) {
      return;
    }

    if (J.isFoliaThreading()) {
      J.s(new Location(chunk.getWorld(), (chunk.getX() << 4) + 8, 64, (chunk.getZ() << 4) + 8), () -> seedChunkNow(chunk), 0);
      return;
    }

    seedChunkNow(chunk);
  }

  private void seedChunkNow(Chunk chunk) {
    if (!chunk.isLoaded()) {
      return;
    }

    UUID worldId = chunk.getWorld().getUID();
    long key = chunkKey(chunk.getX(), chunk.getZ());
    ChunkIndex index = chunkIndexByKey.computeIfAbsent(key, k -> new ChunkIndex());

    for (BlockState state : chunk.getTileEntities()) {
      if (state instanceof Furnace) {
        Location loc = state.getLocation();
        if (index.add(worldId, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), TrackedKind.FURNACE)) {
          activeBlockCount.incrementAndGet();
        }
        continue;
      }

      if (state instanceof BrewingStand) {
        Location loc = state.getLocation();
        if (index.add(worldId, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), TrackedKind.BREWING_STAND)) {
          activeBlockCount.incrementAndGet();
        }
      }
    }
  }

  private TrackedKind classify(String materialName) {
    return switch (materialName) {
      case "FURNACE", "BLAST_FURNACE", "SMOKER" -> TrackedKind.FURNACE;
      case "BREWING_STAND" -> TrackedKind.BREWING_STAND;
      default -> null;
    };
  }

  private static long chunkKey(int cx, int cz) {
    return (((long) cx) << 32) ^ (cz & 0xffffffffL);
  }

  private static long blockKey(int x, int y, int z) {
    return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
  }

  private static int blockKeyChunkX(long blockKey) {
    return signExtend26((int) ((blockKey >>> 38) & 0x3FFFFFFL)) >> 4;
  }

  private static int blockKeyChunkZ(long blockKey) {
    return signExtend26((int) ((blockKey >>> 12) & 0x3FFFFFFL)) >> 4;
  }

  private static int signExtend26(int value) {
    return (value << 6) >> 6;
  }

  private enum TrackedKind {
    FURNACE,
    BREWING_STAND
  }

  private static final class ChunkIndex {
    private final Map<Long, TrackedBlock> blocks = new ConcurrentHashMap<>();

    private boolean add(UUID worldId, int x, int y, int z, TrackedKind kind) {
      long blockKey = blockKey(x, y, z);
      TrackedBlock previous = blocks.put(blockKey, new TrackedBlock(worldId, x, y, z, kind));
      return previous == null;
    }

    private void remove(int x, int y, int z) {
      blocks.remove(blockKey(x, y, z));
    }

    private Collection<TrackedBlock> snapshot() {
      return blocks.values();
    }

    private static long blockKey(int x, int y, int z) {
      return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }
  }

  private static final class TrackedBlock {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final TrackedKind kind;

    private TrackedBlock(UUID worldId, int x, int y, int z, TrackedKind kind) {
      this.worldId = worldId;
      this.x = x;
      this.y = y;
      this.z = z;
      this.kind = kind;
    }
  }
}
