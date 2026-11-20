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

import art.arcane.react.api.feature.PressureGate;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.FurnaceHost;
import art.arcane.react.platform.FurnaceHost.Kind;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import lombok.Getter;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
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
public class FeatureFurnaceBrewBatching extends ReactFeature {
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
  private transient volatile FurnaceHost furnaces;
  private transient volatile BlockHost blocks;
  private transient volatile WorldHost worlds;
  private transient final List<Subscription> subscriptions = new ArrayList<>();
  private transient Subscription tickHooks = Subscription.NONE;

  public FeatureFurnaceBrewBatching() {
    super(ID);
  }

  @Override
  public void onActivate() {
    long generation = lifecycleGeneration.incrementAndGet();
    ReactPlatform platform = ReactPlatforms.get();
    furnaces = platform.furnaceHost().orElse(null);
    blocks = platform.blockHost().orElse(null);
    worlds = platform.worldHost().orElse(null);
    subscribe();
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
    for (Subscription subscription : subscriptions) {
      subscription.cancel();
    }
    subscriptions.clear();
  }

  private void subscribe() {
    BlockHost blockHost = blocks;
    if (blockHost != null) {
      subscriptions.add(blockHost.onChunkLoad(chunk -> queueLoadSeed(ChunkCoordinate.of(chunk))));
      subscriptions.add(blockHost.onChunkUnload(this::onChunkUnload));
      subscriptions.add(blockHost.onWorldUnload(this::onWorldUnload));
      subscriptions.add(blockHost.onPlace(ListenerPriority.MONITOR, true, this::onPlace));
      subscriptions.add(blockHost.onBreak(ListenerPriority.MONITOR, true, this::onBreak));
    }
    FurnaceHost furnaceHost = furnaces;
    if (furnaceHost != null) {
      subscriptions.add(furnaceHost.onActivity(station -> add(station.worldId(), station.x(), station.y(), station.z(), station.kind())));
    }
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
    bridgeActive = false;
    FurnaceHost host = furnaces;
    if (host == null) {
      return;
    }
    FurnaceHost.TickHook furnace = (UUID worldId, int x, int y, int z) -> {
      if (shouldRunVanilla(worldId, x, y, z)) {
        return furnaceSkipDebt.drain(worldId, x, y, z);
      }
      furnaceSkipDebt.add(worldId, x, y, z);
      skippedFurnaceTicks.incrementAndGet();
      return FurnaceHost.SKIP;
    };
    FurnaceHost.TickHook brewing = (UUID worldId, int x, int y, int z) -> {
      if (shouldRunVanilla(worldId, x, y, z)) {
        return brewingSkipDebt.drain(worldId, x, y, z);
      }
      brewingSkipDebt.add(worldId, x, y, z);
      skippedBrewingTicks.incrementAndGet();
      return FurnaceHost.SKIP;
    };
    tickHooks = host.hookTicks(furnace, brewing, installed -> bridgeActive = installed);
  }

  private boolean shouldRunVanilla(UUID worldId, int x, int y, int z) {
    return !gate.isEngaged() || nearPlayer(worldId, x, y, z);
  }

  private boolean nearPlayer(UUID worldId, int x, int y, int z) {
    WorldHost presence = worlds;
    return presence != null && presence.anyPlayerNear(new GameLocation(worldId, x + 0.5D, y + 0.5D, z + 0.5D), bypassRadius);
  }

  private void uninstallBridgeHooks() {
    tickHooks.cancel();
    tickHooks = Subscription.NONE;
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
      ReactComponents.verbose("Furnace/brew batching engaged.");
    } else if (wasEngaged && !nowEngaged) {
      ReactComponents.verbose("Furnace/brew batching released.");
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
      List<TrackedBlock> tracked,
      MeasurementSweep sweep,
      long generation
  ) {
    ReactWorld scheduledWorld = world(coordinate.worldId);
    if (scheduledWorld == null || tracked.isEmpty() || !isActive(generation)) {
      return;
    }
    sweep.inFlight.incrementAndGet();
    boolean scheduled = J.runChunk(scheduledWorld, coordinate.chunkX, coordinate.chunkZ, () -> {
      try {
        if (!isActive(generation) || measurementSweep != sweep) {
          return;
        }
        if (!isChunkLoaded(coordinate)) {
          return;
        }
        long activeBlocks = 0;
        long skippableTicks = 0;
        long bypassed = 0;
        for (TrackedBlock block : tracked) {
          if (nearPlayer(block.worldId, block.x, block.y, block.z)) {
            bypassed++;
            continue;
          }
          long projected = projectSkippableNow(block);
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

  private long projectSkippableNow(TrackedBlock block) {
    FurnaceHost host = furnaces;
    if (host == null) {
      return 0;
    }
    return switch (block.kind) {
      case FURNACE -> host.furnace(block.worldId, block.x, block.y, block.z).map(FeatureFurnaceBrewBatching::projectFurnaceSkippable).orElse(0L);
      case BREWING_STAND -> host.brewing(block.worldId, block.x, block.y, block.z).map(FeatureFurnaceBrewBatching::projectBrewingSkippable).orElse(0L);
    };
  }

  static long projectFurnaceSkippable(FurnaceHost.FurnaceState furnace) {
    int burnTime = furnace.burnTime();
    int cookTime = furnace.cookTime();
    int baseCook = baseCookTicks(furnace.blockType());
    int cookTotal = furnace.cookTimeTotal() > 0 ? furnace.cookTimeTotal() : baseCook;
    int cookRemaining = Math.max(0, cookTotal - cookTime);
    boolean hasFuel = burnTime > 0 || furnace.hasFuelItem();

    if (!hasFuel || !furnace.hasInput()) {
      return 0;
    }

    int next = Math.min(burnTime > 0 ? burnTime : Integer.MAX_VALUE, cookRemaining > 0 ? cookRemaining : Integer.MAX_VALUE);
    if (next == Integer.MAX_VALUE) {
      return 0;
    }

    return Math.max(0, next - 1);
  }

  static long projectBrewingSkippable(FurnaceHost.BrewingState stand) {
    if (!stand.hasIngredient() || !stand.hasBottle()) {
      return 0;
    }

    if (stand.brewingTime() > 0) {
      return Math.max(0, stand.brewingTime() - 1);
    }

    if (stand.fuelLevel() <= 0) {
      return 0;
    }

    return Math.max(0, BREW_TICKS - 1);
  }

  private static int baseCookTicks(String blockType) {
    String name = blockType == null ? "" : blockType.startsWith("minecraft:") ? blockType.substring(10).toUpperCase(java.util.Locale.ROOT) : blockType;
    return switch (name) {
      case "BLAST_FURNACE" -> BLAST_FURNACE_BASE_COOK_TICKS;
      case "SMOKER" -> SMOKER_BASE_COOK_TICKS;
      default -> FURNACE_BASE_COOK_TICKS;
    };
  }

  private void onChunkUnload(ChunkRef chunk) {
    ChunkCoordinate coordinate = ChunkCoordinate.of(chunk);
    removeChunkIndex(coordinate);
    queuedLoadSeeds.remove(coordinate);
    furnaceSkipDebt.evictChunk(coordinate.worldId, coordinate.chunkX, coordinate.chunkZ);
    brewingSkipDebt.evictChunk(coordinate.worldId, coordinate.chunkX, coordinate.chunkZ);
  }

  private void onWorldUnload(UUID worldId) {
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

  private void onPlace(BlockHost.BlockEvent event) {
    Kind kind = Kind.of(event.type());
    if (kind == null) {
      return;
    }

    GameLocation location = event.location();
    add(location.worldId(), location.blockX(), location.blockY(), location.blockZ(), kind);
  }

  private void onBreak(BlockHost.BlockEvent event) {
    Kind kind = Kind.of(event.type());
    if (kind == null) {
      return;
    }

    GameLocation location = event.location();
    UUID worldId = location.worldId();
    int x = location.blockX();
    int y = location.blockY();
    int z = location.blockZ();
    ChunkCoordinate coordinate = new ChunkCoordinate(worldId, x >> 4, z >> 4);
    ChunkIndex index = chunkIndexByKey.get(coordinate);
    if (index != null && index.remove(x, y, z)) {
      trackedBlockCount.decrementAndGet();
      if (index.isEmpty()) {
        removeChunkIndex(coordinate);
      }
    }
    SkipDebtLedger ledger = kind == Kind.FURNACE ? furnaceSkipDebt : brewingSkipDebt;
    ledger.drain(worldId, x, y, z);
  }

  private void add(UUID worldId, int x, int y, int z, Kind kind) {
    if (worldId == null) {
      return;
    }

    ChunkCoordinate coordinate = new ChunkCoordinate(worldId, x >> 4, z >> 4);
    ChunkIndex index = chunkIndexByKey.computeIfAbsent(coordinate, ignored -> new ChunkIndex());
    if (index.add(worldId, x, y, z, kind)) {
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
    BlockHost blockHost = blocks;
    if (remaining <= 0 || blockHost == null) {
      return;
    }
    scheduleReseedTargets(blockHost.nextLoadedChunks(remaining), generation);
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

  private void scheduleReseedTargets(List<ChunkRef> targets, long generation) {
    int remaining = reseedBudget();
    for (ChunkRef target : targets) {
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
    ReactWorld world = world(coordinate.worldId);
    if (world == null) {
      seedTasksInFlight.remove(coordinate);
      return;
    }
    boolean scheduled = J.runChunk(world, coordinate.chunkX, coordinate.chunkZ, () -> {
      try {
        if (!isActive(generation)) {
          return;
        }
        if (!isChunkLoaded(coordinate)) {
          removeChunkIndex(coordinate);
          return;
        }
        seedChunkNow(coordinate, generation);
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

  private void seedChunkNow(ChunkCoordinate coordinate, long generation) {
    FurnaceHost host = furnaces;
    if (host == null || !isActive(generation)) {
      return;
    }

    removeChunkIndex(coordinate);

    for (FurnaceHost.Station station : host.scan(new ChunkRef(coordinate.worldId, coordinate.chunkX, coordinate.chunkZ))) {
      if (!isActive(generation) || trackedBlockCount.get() >= Math.max(1, maxTrackedEntries)) {
        break;
      }
      add(station.worldId(), station.x(), station.y(), station.z(), station.kind());
    }
  }

  private ReactWorld world(UUID worldId) {
    WorldHost host = worlds;
    return host == null ? null : host.world(worldId).orElse(null);
  }

  private boolean isChunkLoaded(ChunkCoordinate coordinate) {
    BlockHost host = blocks;
    return host != null && host.isChunkLoaded(coordinate.worldId, coordinate.chunkX, coordinate.chunkZ);
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

  private static long blockKey(int x, int y, int z) {
    return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
  }

  private static long chunkKey(int chunkX, int chunkZ) {
    return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
  }

  private static final class ChunkIndex {
    private final LinkedHashMap<Long, TrackedBlock> blocks = new LinkedHashMap<>();

    private synchronized boolean add(UUID worldId, int x, int y, int z, Kind kind) {
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

    private synchronized void add(UUID worldId, int x, int y, int z) {
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

    private int drain(UUID worldId, int x, int y, int z) {
      if (outstanding == 0) {
        return 0;
      }
      synchronized (this) {
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
    private static ChunkCoordinate of(ChunkRef chunk) {
      return new ChunkCoordinate(chunk.worldId(), chunk.chunkX(), chunk.chunkZ());
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
    private final Kind kind;

    private TrackedBlock(UUID worldId, int x, int y, int z, Kind kind) {
      this.worldId = worldId;
      this.x = x;
      this.y = y;
      this.z = z;
      this.kind = kind;
    }
  }
}
