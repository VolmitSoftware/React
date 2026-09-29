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
import art.arcane.volmlib.nativelib.monitor.BrewingTickHook;
import art.arcane.volmlib.nativelib.monitor.BrewingTickResult;
import art.arcane.volmlib.nativelib.monitor.FurnaceTickHook;
import art.arcane.volmlib.nativelib.monitor.FurnaceTickResult;
import art.arcane.volmlib.nativelib.monitor.NativeMonitor;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.ObserverController.LoadedChunkTarget;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.react.util.project.world.BlockEntityScan;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Configuration for Furnace/Brew Batching feature. Tracks active furnaces and brewing stands per chunk and measures the number of intermediate ticks that could be skipped between fuel-out / cook-complete events. Reports projected skip volume as a runtime metric; bypasses any block within bypassRadius of a player so cosmetic flame/smoke remains lively near players.")
public class FeatureFurnaceBrewBatching extends ReactFeature implements Listener {
  public static final String ID = "furnace-brew-batching";
  private static final int FURNACE_BASE_COOK_TICKS = 200;
  private static final int BLAST_FURNACE_BASE_COOK_TICKS = 100;
  private static final int SMOKER_BASE_COOK_TICKS = 100;
  private static final int BREW_TICKS = 400;
  private static final int BREW_FUEL_BURN_TICKS = 20;
  private static final int MAX_RESEED_CHUNKS_PER_TICK = 256;
  private static final int MAX_MEASUREMENT_ENTRIES_PER_TICK = 512;
  private static final int MAX_MEASUREMENT_TASKS_PER_TICK = 32;
  private static final int MAX_PENDING_LOAD_SEEDS = 8192;
  private static final Set<Material> TRACKED_MATERIALS = EnumSet.of(
      Material.FURNACE,
      Material.BLAST_FURNACE,
      Material.SMOKER,
      Material.BREWING_STAND
  );

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

  private transient final Map<ChunkCoordinate, ChunkIndex> chunkIndexByKey = new ConcurrentHashMap<>();
  private transient final Queue<MeasurementChunkToken> measurementChunkRotation = new ConcurrentLinkedQueue<>();
  private transient final Map<ChunkCoordinate, MeasurementChunkToken> measurementChunks = new ConcurrentHashMap<>();
  private transient final Set<ChunkCoordinate> seedTasksInFlight = ConcurrentHashMap.newKeySet();
  private transient final Queue<ChunkCoordinate> pendingLoadSeeds = new ArrayBlockingQueue<>(MAX_PENDING_LOAD_SEEDS);
  private transient final Set<ChunkCoordinate> queuedLoadSeeds = ConcurrentHashMap.newKeySet();
  private transient final AtomicLong trackedBlockCount = new AtomicLong();
  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  private transient final AtomicLong projectedSkippableTicks = new AtomicLong();
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
  private transient final SkipDebtLedger furnaceSkipDebt = new SkipDebtLedger();
  private transient final SkipDebtLedger brewingSkipDebt = new SkipDebtLedger();
  private transient volatile boolean active;
  private transient volatile MeasurementSweep measurementSweep;

  public FeatureFurnaceBrewBatching() {
    super(ID);
  }

  @Override
  public void onActivate() {
    long generation = lifecycleGeneration.incrementAndGet();
    active = true;
    chunkIndexByKey.clear();
    measurementChunkRotation.clear();
    measurementChunks.clear();
    seedTasksInFlight.clear();
    pendingLoadSeeds.clear();
    queuedLoadSeeds.clear();
    trackedBlockCount.set(0);
    projectedSkippableTicks.set(0);
    skippedFurnaceTicks.set(0);
    skippedBrewingTicks.set(0);
    furnaceSkipDebt.clear();
    brewingSkipDebt.clear();
    lastTotalActive = 0;
    lastTotalProjectedSkippableTicks = 0;
    lastBypassedByPlayer = 0;
    measurementSweep = null;
    gate.reset();
    scheduleReseedBatch(generation);
    installBridgeHooks();
  }

  @Override
  public void onDeactivate() {
    active = false;
    lifecycleGeneration.incrementAndGet();
    chunkIndexByKey.clear();
    measurementChunkRotation.clear();
    measurementChunks.clear();
    seedTasksInFlight.clear();
    pendingLoadSeeds.clear();
    queuedLoadSeeds.clear();
    trackedBlockCount.set(0);
    projectedSkippableTicks.set(0);
    measurementSweep = null;
    gate.reset();
    furnaceSkipDebt.clear();
    brewingSkipDebt.clear();
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
    NativeMonitor bridge = NmsBridges.get();
    if (bridge == null) {
      bridgeActive = false;
      return;
    }
    FurnaceTickHook furnace = (World world, int x, int y, int z) -> {
      if (shouldRunVanilla(world, x, y, z)) {
        return FurnaceTickResult.runAndAdvance(furnaceSkipDebt.drain(world, x, y, z));
      }
      furnaceSkipDebt.add(world, x, y, z);
      skippedFurnaceTicks.incrementAndGet();
      return FurnaceTickResult.SKIP;
    };
    BrewingTickHook brewing = (World world, int x, int y, int z) -> {
      if (shouldRunVanilla(world, x, y, z)) {
        return BrewingTickResult.runAndAdvance(brewingSkipDebt.drain(world, x, y, z));
      }
      brewingSkipDebt.add(world, x, y, z);
      skippedBrewingTicks.incrementAndGet();
      return BrewingTickResult.SKIP;
    };
    boolean furnaceOk = bridge.installFurnaceTickHook(furnace);
    boolean brewingOk = bridge.installBrewingTickHook(brewing);
    bridgeActive = furnaceOk || brewingOk;
  }

  private boolean shouldRunVanilla(World world, int x, int y, int z) {
    return !gate.isEngaged()
        || React.hasNearbyPlayer(new Location(world, x + 0.5D, y + 0.5D, z + 0.5D), bypassRadius);
  }

  private void uninstallBridgeHooks() {
    NativeMonitor bridge = NmsBridges.get();
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
    long generation = lifecycleGeneration.get();
    if (!isActive(generation)) {
      return;
    }
    scheduleReseedBatch(generation);
    updateEngagement();
    if (!gate.isEngaged()) {
      measurementSweep = null;
      lastTotalActive = 0;
      lastTotalProjectedSkippableTicks = 0;
      lastBypassedByPlayer = 0;
      return;
    }

    measure(generation);
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

  private void measure(long generation) {
    MeasurementSweep sweep = measurementSweep;
    if (sweep != null && sweep.schedulingComplete && sweep.inFlight.get() == 0) {
      lastTotalActive = sweep.activeBlocks.get();
      lastTotalProjectedSkippableTicks = sweep.projectedSkippableTicks.get();
      lastBypassedByPlayer = sweep.bypassedByPlayer.get();
      projectedSkippableTicks.set(lastTotalProjectedSkippableTicks);
      measurementSweep = null;
      sweep = null;
    }
    if (sweep == null) {
      sweep = new MeasurementSweep(measurementChunks.size());
      measurementSweep = sweep;
    }

    int entryBudget = MAX_MEASUREMENT_ENTRIES_PER_TICK;
    int taskBudget = MAX_MEASUREMENT_TASKS_PER_TICK;
    while (entryBudget > 0 && taskBudget > 0 && sweep.chunksRemaining > 0) {
      MeasurementWork work = sweep.currentWork;
      if (work == null) {
        MeasurementChunkToken token = measurementChunkRotation.poll();
        if (token == null) {
          sweep.chunksRemaining = 0;
          break;
        }
        ChunkCoordinate coordinate = token.coordinate;
        if (measurementChunks.get(coordinate) != token) {
          sweep.chunksRemaining--;
          continue;
        }
        measurementChunkRotation.offer(token);
        ChunkIndex index = chunkIndexByKey.get(coordinate);
        int remaining = index == null ? 0 : index.size();
        work = new MeasurementWork(coordinate, remaining);
        sweep.currentWork = work;
      }

      ChunkIndex index = chunkIndexByKey.get(work.coordinate);
      if (index == null || work.remaining <= 0) {
        sweep.currentWork = null;
        sweep.chunksRemaining--;
        continue;
      }
      int maximum = Math.min(entryBudget, work.remaining);
      List<TrackedBlock> blocks = index.next(maximum);
      work.remaining -= blocks.size();
      if (blocks.isEmpty()) {
        work.remaining = 0;
      } else {
        dispatchChunkMeasurement(work.coordinate, blocks, sweep, generation);
        entryBudget -= blocks.size();
        taskBudget--;
      }
      if (work.remaining <= 0) {
        sweep.currentWork = null;
        sweep.chunksRemaining--;
      }
    }
    sweep.schedulingComplete = sweep.chunksRemaining <= 0;
  }

  private void dispatchChunkMeasurement(
      ChunkCoordinate coordinate,
      List<TrackedBlock> blocks,
      MeasurementSweep sweep,
      long generation
  ) {
    World scheduledWorld = Bukkit.getWorld(coordinate.worldId);
    if (scheduledWorld == null || blocks.isEmpty() || !isActive(generation)) {
      return;
    }
    sweep.inFlight.incrementAndGet();
    boolean scheduled = J.runChunk(scheduledWorld, coordinate.chunkX, coordinate.chunkZ, () -> {
      try {
        if (!isActive(generation) || measurementSweep != sweep) {
          return;
        }
        World world = Bukkit.getWorld(coordinate.worldId);
        if (world == null || !world.isChunkLoaded(coordinate.chunkX, coordinate.chunkZ)) {
          return;
        }
        Chunk chunk = world.getChunkAt(coordinate.chunkX, coordinate.chunkZ);
        long activeBlocks = 0;
        long skippableTicks = 0;
        long bypassed = 0;
        for (TrackedBlock block : blocks) {
          Location location = new Location(world, block.x + 0.5D, block.y + 0.5D, block.z + 0.5D);
          if (React.hasNearbyPlayer(location, bypassRadius)) {
            bypassed++;
            continue;
          }
          long projected = projectSkippableNow(chunk, block);
          if (projected > 0) {
            activeBlocks++;
            skippableTicks += projected;
          }
        }
        sweep.activeBlocks.addAndGet(activeBlocks);
        sweep.projectedSkippableTicks.addAndGet(skippableTicks);
        sweep.bypassedByPlayer.addAndGet(bypassed);
      } finally {
        sweep.inFlight.decrementAndGet();
      }
    });
    if (!scheduled) {
      sweep.inFlight.decrementAndGet();
    }
  }

  private long projectSkippableNow(Chunk chunk, TrackedBlock block) {
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

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkLoadEvent event) {
    queueLoadSeed(ChunkCoordinate.of(event.getChunk()));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkUnloadEvent event) {
    ChunkCoordinate coordinate = ChunkCoordinate.of(event.getChunk());
    removeChunkIndex(coordinate);
    queuedLoadSeeds.remove(coordinate);
    furnaceSkipDebt.evictChunk(coordinate.worldId, coordinate.chunkX, coordinate.chunkZ);
    brewingSkipDebt.evictChunk(coordinate.worldId, coordinate.chunkX, coordinate.chunkZ);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(WorldUnloadEvent event) {
    UUID worldId = event.getWorld().getUID();
    for (ChunkCoordinate coordinate : new ArrayList<>(chunkIndexByKey.keySet())) {
      if (coordinate.worldId.equals(worldId)) {
        removeChunkIndex(coordinate);
      }
    }
    seedTasksInFlight.removeIf(coordinate -> coordinate.worldId.equals(worldId));
    queuedLoadSeeds.removeIf(coordinate -> coordinate.worldId.equals(worldId));
    furnaceSkipDebt.evictWorld(worldId);
    brewingSkipDebt.evictWorld(worldId);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent event) {
    Block block = event.getBlock();
    TrackedKind kind = trackedKind(block.getType());
    if (kind == null) {
      return;
    }

    add(block.getWorld(), block.getX(), block.getY(), block.getZ(), kind);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent event) {
    Block block = event.getBlock();
    TrackedKind kind = trackedKind(block.getType());
    if (kind == null) {
      return;
    }

    World world = block.getWorld();
    int x = block.getX();
    int y = block.getY();
    int z = block.getZ();
    ChunkCoordinate coordinate = new ChunkCoordinate(world.getUID(), x >> 4, z >> 4);
    ChunkIndex index = chunkIndexByKey.get(coordinate);
    if (index != null && index.remove(x, y, z)) {
      trackedBlockCount.decrementAndGet();
      if (index.isEmpty()) {
        removeChunkIndex(coordinate);
      }
    }
    SkipDebtLedger ledger = kind == TrackedKind.FURNACE ? furnaceSkipDebt : brewingSkipDebt;
    ledger.drain(world, x, y, z);
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
    TrackedKind kind = trackedKind(inventory);
    if (kind == null) {
      return;
    }

    Location loc = inventory.getLocation();
    if (loc == null) {
      return;
    }

    add(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), kind);
  }

  private void add(World world, int x, int y, int z, TrackedKind kind) {
    if (world == null) {
      return;
    }

    ChunkCoordinate coordinate = new ChunkCoordinate(world.getUID(), x >> 4, z >> 4);
    ChunkIndex index = chunkIndexByKey.computeIfAbsent(coordinate, ignored -> new ChunkIndex());
    if (index.add(world.getUID(), x, y, z, kind)) {
      long tracked = trackedBlockCount.incrementAndGet();
      if (tracked > Math.max(1, maxTrackedEntries)) {
        if (index.remove(x, y, z)) {
          trackedBlockCount.decrementAndGet();
        }
        if (index.isEmpty()) {
          chunkIndexByKey.remove(coordinate, index);
        }
        return;
      }
      registerMeasurementChunk(coordinate);
    }
  }

  private void queueLoadSeed(ChunkCoordinate coordinate) {
    if (!active || !queuedLoadSeeds.add(coordinate)) {
      return;
    }
    if (!pendingLoadSeeds.offer(coordinate)) {
      queuedLoadSeeds.remove(coordinate);
    }
  }

  private void scheduleReseedBatch(long generation) {
    if (!isActive(generation)) {
      return;
    }
    int remaining = scheduleLoadSeeds(generation);
    if (remaining <= 0 || React.instance == null) {
      return;
    }
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null) {
      return;
    }
    scheduleReseedTargets(observer.nextLoadedChunkCoordinateBatch(remaining), generation);
  }

  private int scheduleLoadSeeds(long generation) {
    int budget = reseedBudget();
    int loadBudget = Math.max(1, budget - Math.max(1, budget / 4));
    int scheduled = 0;
    while (scheduled < loadBudget && isActive(generation)) {
      ChunkCoordinate coordinate = pendingLoadSeeds.poll();
      if (coordinate == null) {
        break;
      }
      if (queuedLoadSeeds.remove(coordinate)) {
        scheduleSeed(coordinate, generation);
        scheduled++;
      }
    }
    return budget - scheduled;
  }

  private void scheduleReseedTargets(List<LoadedChunkTarget> targets, long generation) {
    int remaining = reseedBudget();
    for (LoadedChunkTarget target : targets) {
      if (remaining-- <= 0 || !isActive(generation)) {
        break;
      }
      scheduleSeed(new ChunkCoordinate(target.worldId(), target.chunkX(), target.chunkZ()), generation);
    }
  }

  private void scheduleSeed(ChunkCoordinate coordinate, long generation) {
    if (!isActive(generation) || !seedTasksInFlight.add(coordinate)) {
      return;
    }
    World world = Bukkit.getWorld(coordinate.worldId);
    if (world == null) {
      seedTasksInFlight.remove(coordinate);
      return;
    }
    boolean scheduled = J.runChunk(world, coordinate.chunkX, coordinate.chunkZ, () -> {
      try {
        if (!isActive(generation)) {
          return;
        }
        World currentWorld = Bukkit.getWorld(coordinate.worldId);
        if (currentWorld == null || !currentWorld.isChunkLoaded(coordinate.chunkX, coordinate.chunkZ)) {
          removeChunkIndex(coordinate);
          return;
        }
        seedChunkNow(currentWorld.getChunkAt(coordinate.chunkX, coordinate.chunkZ), generation);
      } finally {
        if (generation == lifecycleGeneration.get()) {
          seedTasksInFlight.remove(coordinate);
        }
      }
    });
    if (!scheduled) {
      seedTasksInFlight.remove(coordinate);
    }
  }

  private void seedChunkNow(Chunk chunk, long generation) {
    if (chunk == null || !chunk.isLoaded() || !isActive(generation)) {
      return;
    }

    World world = chunk.getWorld();
    ChunkCoordinate coordinate = new ChunkCoordinate(world.getUID(), chunk.getX(), chunk.getZ());
    removeChunkIndex(coordinate);

    for (BlockState state : BlockEntityScan.runtime().scan(chunk, TRACKED_MATERIALS)) {
      if (!isActive(generation) || trackedBlockCount.get() >= Math.max(1, maxTrackedEntries)) {
        break;
      }
      TrackedKind kind = trackedKind(state.getType());
      if (kind != null) {
        add(world, state.getX(), state.getY(), state.getZ(), kind);
      }
    }
  }

  private void registerMeasurementChunk(ChunkCoordinate coordinate) {
    MeasurementChunkToken token = new MeasurementChunkToken(coordinate);
    if (measurementChunks.putIfAbsent(coordinate, token) == null) {
      measurementChunkRotation.offer(token);
    }
  }

  private void removeChunkIndex(ChunkCoordinate coordinate) {
    ChunkIndex removed = chunkIndexByKey.remove(coordinate);
    if (removed != null) {
      trackedBlockCount.addAndGet(-removed.size());
    }
    measurementChunks.remove(coordinate);
  }

  private boolean isActive(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  private int reseedBudget() {
    return Math.max(1, Math.min(reseedChunksPerTick, MAX_RESEED_CHUNKS_PER_TICK));
  }

  private static TrackedKind trackedKind(Material material) {
    return switch (material) {
      case FURNACE, BLAST_FURNACE, SMOKER -> TrackedKind.FURNACE;
      case BREWING_STAND -> TrackedKind.BREWING_STAND;
      default -> null;
    };
  }

  private static TrackedKind trackedKind(Inventory inventory) {
    if (inventory instanceof FurnaceInventory) {
      return TrackedKind.FURNACE;
    }
    if (inventory instanceof BrewerInventory) {
      return TrackedKind.BREWING_STAND;
    }
    return null;
  }

  private static long blockKey(int x, int y, int z) {
    return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
  }

  private static long chunkKey(int chunkX, int chunkZ) {
    return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
  }

  private enum TrackedKind {
    FURNACE,
    BREWING_STAND
  }

  private static final class ChunkIndex {
    private final LinkedHashMap<Long, TrackedBlock> blocks = new LinkedHashMap<>();

    private synchronized boolean add(UUID worldId, int x, int y, int z, TrackedKind kind) {
      long blockKey = blockKey(x, y, z);
      TrackedBlock previous = blocks.put(blockKey, new TrackedBlock(worldId, x, y, z, kind));
      return previous == null;
    }

    private synchronized boolean remove(int x, int y, int z) {
      return blocks.remove(blockKey(x, y, z)) != null;
    }

    private synchronized int size() {
      return blocks.size();
    }

    private synchronized boolean isEmpty() {
      return blocks.isEmpty();
    }

    private synchronized List<TrackedBlock> next(int maximum) {
      int count = Math.min(Math.max(0, maximum), blocks.size());
      List<TrackedBlock> result = new ArrayList<>(count);
      for (int index = 0; index < count; index++) {
        Map.Entry<Long, TrackedBlock> first = blocks.entrySet().iterator().next();
        blocks.remove(first.getKey());
        blocks.put(first.getKey(), first.getValue());
        result.add(first.getValue());
      }
      return result;
    }

    private static long blockKey(int x, int y, int z) {
      return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }
  }

  private static final class SkipDebtLedger {
    private final Map<UUID, Long2ObjectOpenHashMap<Long2IntOpenHashMap>> debtByWorld = new HashMap<>();
    private volatile int outstanding;

    private synchronized void add(World world, int x, int y, int z) {
      UUID worldId = world.getUID();
      Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks = debtByWorld.get(worldId);
      if (chunks == null) {
        chunks = new Long2ObjectOpenHashMap<>();
        debtByWorld.put(worldId, chunks);
      }
      long chunk = chunkKey(x >> 4, z >> 4);
      Long2IntOpenHashMap blocks = chunks.get(chunk);
      if (blocks == null) {
        blocks = new Long2IntOpenHashMap();
        chunks.put(chunk, blocks);
      }
      if (blocks.addTo(blockKey(x, y, z), 1) == 0) {
        outstanding = outstanding + 1;
      }
    }

    private int drain(World world, int x, int y, int z) {
      if (outstanding == 0) {
        return 0;
      }
      synchronized (this) {
        UUID worldId = world.getUID();
        Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks = debtByWorld.get(worldId);
        if (chunks == null) {
          return 0;
        }
        long chunk = chunkKey(x >> 4, z >> 4);
        Long2IntOpenHashMap blocks = chunks.get(chunk);
        if (blocks == null) {
          return 0;
        }
        int debt = blocks.remove(blockKey(x, y, z));
        if (debt <= 0) {
          return 0;
        }
        outstanding = outstanding - 1;
        if (blocks.isEmpty()) {
          chunks.remove(chunk);
          if (chunks.isEmpty()) {
            debtByWorld.remove(worldId);
          }
        }
        return debt;
      }
    }

    private void evictChunk(UUID worldId, int chunkX, int chunkZ) {
      if (outstanding == 0) {
        return;
      }
      synchronized (this) {
        Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks = debtByWorld.get(worldId);
        if (chunks == null) {
          return;
        }
        Long2IntOpenHashMap blocks = chunks.remove(chunkKey(chunkX, chunkZ));
        if (blocks == null) {
          return;
        }
        outstanding = outstanding - blocks.size();
        if (chunks.isEmpty()) {
          debtByWorld.remove(worldId);
        }
      }
    }

    private synchronized void evictWorld(UUID worldId) {
      Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks = debtByWorld.remove(worldId);
      if (chunks == null) {
        return;
      }
      for (Long2IntOpenHashMap blocks : chunks.values()) {
        outstanding = outstanding - blocks.size();
      }
    }

    private synchronized void clear() {
      debtByWorld.clear();
      outstanding = 0;
    }
  }

  private record ChunkCoordinate(UUID worldId, int chunkX, int chunkZ) {
    private static ChunkCoordinate of(Chunk chunk) {
      return new ChunkCoordinate(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
  }

  private static final class MeasurementSweep {
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong activeBlocks = new AtomicLong();
    private final AtomicLong projectedSkippableTicks = new AtomicLong();
    private final AtomicLong bypassedByPlayer = new AtomicLong();
    private int chunksRemaining;
    private MeasurementWork currentWork;
    private volatile boolean schedulingComplete;

    private MeasurementSweep(int chunksRemaining) {
      this.chunksRemaining = chunksRemaining;
    }
  }

  private static final class MeasurementWork {
    private final ChunkCoordinate coordinate;
    private int remaining;

    private MeasurementWork(ChunkCoordinate coordinate, int remaining) {
      this.coordinate = coordinate;
      this.remaining = remaining;
    }
  }

  private static final class MeasurementChunkToken {
    private final ChunkCoordinate coordinate;

    private MeasurementChunkToken(ChunkCoordinate coordinate) {
      this.coordinate = coordinate;
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
