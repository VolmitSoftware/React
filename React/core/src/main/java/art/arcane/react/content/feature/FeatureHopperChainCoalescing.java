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

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.Facing;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

@ConfigDescription("Configuration for Hopper Chain Coalescing feature. Detects linear hopper chains and reports projected tick savings. It remains measurement-only by default; in act mode with the versioned hopper hook, eligible intermediate ticks are skipped and one head-to-tail transfer is synthesized. Synthesized transfers coordinate with FeatureHopperTokenBucket gating instead of cancelling InventoryMoveItemEvent directly.")
public class FeatureHopperChainCoalescing extends ReactFeature {
  public static final String ID = "hopper-chain-coalescing";
  private static final int MAX_CHAIN_LENGTH = 256;
  private static final int CHAIN_REBUILD_DEBOUNCE_MS = 250;
  private static final int MAX_PENDING_REPAIRS = 8192;
  private static final int MAX_PENDING_TRANSFERS = 8192;
  private static final int MAX_COORDINATE_REPAIRS_PER_TICK = 256;
  private static final int MAX_CHAIN_TRANSFERS_PER_TICK = 128;
  private static final Facing[] UPSTREAM_FACES = {
      Facing.NORTH,
      Facing.SOUTH,
      Facing.EAST,
      Facing.WEST,
      Facing.UP
  };
  private static final Set<String> CONTAINER_MATERIALS = Set.of(
      "BARREL",
      "BLAST_FURNACE",
      "BREWING_STAND",
      "CHEST",
      "CHISELED_BOOKSHELF",
      "COPPER_CHEST",
      "EXPOSED_COPPER_CHEST",
      "WEATHERED_COPPER_CHEST",
      "OXIDIZED_COPPER_CHEST",
      "WAXED_COPPER_CHEST",
      "WAXED_EXPOSED_COPPER_CHEST",
      "WAXED_WEATHERED_COPPER_CHEST",
      "WAXED_OXIDIZED_COPPER_CHEST",
      "CRAFTER",
      "DECORATED_POT",
      "DISPENSER",
      "DROPPER",
      "FURNACE",
      "HOPPER",
      "JUKEBOX",
      "LECTERN",
      "SMOKER",
      "TRAPPED_CHEST",
      "SHULKER_BOX",
      "WHITE_SHULKER_BOX",
      "ORANGE_SHULKER_BOX",
      "MAGENTA_SHULKER_BOX",
      "LIGHT_BLUE_SHULKER_BOX",
      "YELLOW_SHULKER_BOX",
      "LIME_SHULKER_BOX",
      "PINK_SHULKER_BOX",
      "GRAY_SHULKER_BOX",
      "LIGHT_GRAY_SHULKER_BOX",
      "CYAN_SHULKER_BOX",
      "PURPLE_SHULKER_BOX",
      "BLUE_SHULKER_BOX",
      "BROWN_SHULKER_BOX",
      "GREEN_SHULKER_BOX",
      "RED_SHULKER_BOX",
      "BLACK_SHULKER_BOX",
      "ACACIA_SHELF",
      "BAMBOO_SHELF",
      "BIRCH_SHELF",
      "CHERRY_SHELF",
      "CRIMSON_SHELF",
      "DARK_OAK_SHELF",
      "JUNGLE_SHELF",
      "MANGROVE_SHELF",
      "OAK_SHELF",
      "PALE_OAK_SHELF",
      "SPRUCE_SHELF",
      "WARPED_SHELF"
  );

  @ConfigDoc(value = "Main evaluation interval for hopper chain coalescing in milliseconds.", impact = "Lower values rebuild stale chains faster but consume more CPU; higher values reduce overhead.")
  private int tickIntervalMS = 1000;
  @ConfigDoc(value = "Bypass radius around players in blocks; chains touching this radius skip coalescing accounting.", impact = "Higher values protect a wider zone around players; lower values count chains closer to players.")
  private int bypassRadius = 16;
  @ConfigDoc(value = "Minimum chain length to count as a coalesceable chain.", impact = "Higher values count only large sorting chains; lower values include shorter chains at extra accounting cost.")
  private int minChainLength = 4;
  @ConfigDoc(value = "Interval before a chunk coordinate becomes eligible for maintenance repair (ticks).", impact = "Higher values rely longer on block and chunk events; lower values revisit indexed coordinates more often.")
  private int rebuildIntervalTicks = 200;
  @ConfigDoc(value = "Maximum chunk coordinates repaired per maintenance tick.", impact = "Higher values discover existing hopper chains faster; lower values spread region work across more ticks.")
  private int repairChunksPerTick = 32;
  @ConfigDoc(value = "Trigger threshold for engaging coalescing accounting rather than passive detection (incident score).", impact = "Higher values reserve coalescing for severe incidents; lower values engage earlier.")
  private double engageOnIncident = 60;
  @ConfigDoc(value = "Tick-time threshold for engaging coalescing accounting (milliseconds).", impact = "Higher values delay activation; lower values make this threshold easier to cross.")
  private double engageOnTickMs = 58;
  @ConfigDoc(value = "Tick-time threshold for releasing back to passive detection (milliseconds).", impact = "Lower values hold accounting longer for stability; higher values restore baseline sooner.")
  private double releaseOnTickMs = 45;
  @ConfigDoc(value = "Skips intermediate hopper tick processing when chains are fast-path eligible (act mode).", impact = "Enabling actually coalesces head-source->tail-recipient transfers; disabling stays in measurement-only mode.")
  private boolean featureActMode = false;
  @ConfigDoc(value = "Bypass FeatureHopperTokenBucket gating when synthesizing head-to-tail transfers (act mode only).", impact = "Enabling lets coalesced transfers run independent of bucket budget; disabling drops the chain back to vanilla ticking when the bucket would reject the move.")
  private boolean featureBucketBypass = false;

  private transient Map<UUID, Long2ObjectOpenHashMap<HopperChain>> chainsByWorldChunk;
  private transient Map<ChunkCoordinate, Set<Long>> chainHeadsByChunk;
  private transient Map<UUID, Long2ObjectOpenHashMap<HopperNode>> hopperNodesByWorld;
  private transient Map<ChunkCoordinate, long[]> hopperPositionsByChunk;
  private transient Map<ChunkCoordinate, Long> lastRepairTickByChunk;
  private transient Map<ChunkCoordinate, Long> rebuildDebounce;
  private transient Queue<ChunkCoordinate> repairQueue;
  private transient Set<ChunkCoordinate> queuedRepairs;
  private transient Queue<ChainTarget> transferRotation;
  private transient Set<ChainTarget> queuedTransfers;
  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  private transient final AtomicLong chainsDetected = new AtomicLong(0L);
  private transient final AtomicLong chainLengthSum = new AtomicLong(0L);
  private transient final AtomicLong fastPathChainCount = new AtomicLong(0L);
  private transient final DoubleAdder ticksSavedAccumulator = new DoubleAdder();
  private transient final AtomicLong ticksSavedPerEvaluation = new AtomicLong(0L);
  private transient volatile long tickCounter;
  private transient volatile boolean engaged;
  private transient volatile boolean active;
  private transient volatile FeatureHopperTokenBucket hopperTokenBucket;
  private transient Subscription tickFilter = Subscription.NONE;
  private transient Subscription blockEvents = Subscription.NONE;
  private transient volatile HopperHost hoppers;
  private transient volatile BlockHost blocks;
  private transient volatile WorldHost worlds;
  @Getter
  private transient volatile boolean bridgeActive;
  private transient final AtomicLong skippedHopperTicks = new AtomicLong(0L);
  private transient final AtomicLong synthesizedTransfers = new AtomicLong(0L);
  private transient final Map<UUID, Long2ObjectOpenHashMap<HopperChain>> middleHopperToChain = new ConcurrentHashMap<>();

  public FeatureHopperChainCoalescing() {
    super(ID);
  }

  @Override
  public void onActivate() {
    lifecycleGeneration.incrementAndGet();
    active = true;
    chainsByWorldChunk = new ConcurrentHashMap<>();
    chainHeadsByChunk = new ConcurrentHashMap<>();
    hopperNodesByWorld = new ConcurrentHashMap<>();
    hopperPositionsByChunk = new ConcurrentHashMap<>();
    lastRepairTickByChunk = new ConcurrentHashMap<>();
    rebuildDebounce = new ConcurrentHashMap<>();
    repairQueue = new ArrayBlockingQueue<>(MAX_PENDING_REPAIRS);
    queuedRepairs = ConcurrentHashMap.newKeySet();
    transferRotation = new ArrayBlockingQueue<>(MAX_PENDING_TRANSFERS);
    queuedTransfers = ConcurrentHashMap.newKeySet();
    chainsDetected.set(0L);
    chainLengthSum.set(0L);
    fastPathChainCount.set(0L);
    ticksSavedAccumulator.reset();
    ticksSavedPerEvaluation.set(0L);
    skippedHopperTicks.set(0L);
    synthesizedTransfers.set(0L);
    middleHopperToChain.clear();
    tickCounter = 0L;
    engaged = false;
    hopperTokenBucket = resolveHopperTokenBucket();
    ReactPlatform platform = ReactPlatforms.get();
    hoppers = platform.hopperHost().orElse(null);
    blocks = platform.blockHost().orElse(null);
    worlds = platform.worldHost().orElse(null);
    subscribeBlockEvents();
    installBridgeHook();
  }

  @Override
  public void onDeactivate() {
    active = false;
    lifecycleGeneration.incrementAndGet();
    if (chainsByWorldChunk != null) {
      chainsByWorldChunk.clear();
    }
    if (chainHeadsByChunk != null) {
      chainHeadsByChunk.clear();
    }
    if (hopperNodesByWorld != null) {
      hopperNodesByWorld.clear();
    }
    if (hopperPositionsByChunk != null) {
      hopperPositionsByChunk.clear();
    }
    if (lastRepairTickByChunk != null) {
      lastRepairTickByChunk.clear();
    }
    if (rebuildDebounce != null) {
      rebuildDebounce.clear();
    }
    if (repairQueue != null) {
      repairQueue.clear();
    }
    if (queuedRepairs != null) {
      queuedRepairs.clear();
    }
    if (transferRotation != null) {
      transferRotation.clear();
    }
    if (queuedTransfers != null) {
      queuedTransfers.clear();
    }
    chainsDetected.set(0L);
    chainLengthSum.set(0L);
    fastPathChainCount.set(0L);
    ticksSavedPerEvaluation.set(0L);
    middleHopperToChain.clear();
    hopperTokenBucket = null;
    blockEvents.cancel();
    blockEvents = Subscription.NONE;
    uninstallBridgeHook();
  }

  private void subscribeBlockEvents() {
    BlockHost blockHost = blocks;
    if (blockHost == null) {
      return;
    }
    List<Subscription> subscriptions = List.of(
        blockHost.onPlace(ListenerPriority.MONITOR, true, event -> queueAffectedChunkRepairs(event.location(), event.type())),
        blockHost.onBreak(ListenerPriority.MONITOR, true, event -> queueAffectedChunkRepairs(event.location(), event.type())),
        blockHost.onPiston(ListenerPriority.MONITOR, true, this::onPiston),
        blockHost.onChunkLoad(this::onChunkLoad),
        blockHost.onChunkUnload(this::onChunkUnload),
        blockHost.onWorldUnload(this::onWorldUnload));
    blockEvents = () -> subscriptions.forEach(Subscription::cancel);
  }

  public boolean isMeasurementOnly() {
    return !featureActMode || !bridgeActive;
  }

  public long readAndResetSkippedHopperTicks() {
    return skippedHopperTicks.getAndSet(0L);
  }

  public long readAndResetSynthesizedTransfers() {
    return synthesizedTransfers.getAndSet(0L);
  }

  private void installBridgeHook() {
    bridgeActive = false;
    if (!featureActMode) {
      return;
    }
    HopperHost host = hoppers;
    if (host == null) {
      return;
    }
    tickFilter = host.installTickFilter(this::skipsTick, installed -> bridgeActive = installed);
  }

  boolean skipsTick(UUID worldId, int x, int y, int z) {
    if (!engaged) {
      return false;
    }
    Long2ObjectOpenHashMap<HopperChain> mapping = middleHopperToChain.get(worldId);
    if (mapping == null) {
      return false;
    }
    long packed = packPos(x, y, z);
    HopperChain chain;
    synchronized (mapping) {
      chain = mapping.get(packed);
    }
    if (chain == null || !chain.fastPathEligible || chain.bypassedByPlayer) {
      return false;
    }
    if (chain.length() < 3) {
      return false;
    }
    if (requiresBucketPermit() && !chain.bucketPermit) {
      return false;
    }
    long head = chain.positions[0];
    long tail = chain.positions[chain.length() - 1];
    if (packed == head || packed == tail) {
      return false;
    }
    skippedHopperTicks.incrementAndGet();
    return true;
  }

  private void uninstallBridgeHook() {
    tickFilter.cancel();
    tickFilter = Subscription.NONE;
    bridgeActive = false;
  }

  private void synthesizeChainTransfers(long generation) {
    int remaining = MAX_CHAIN_TRANSFERS_PER_TICK;
    while (remaining-- > 0 && isActive(generation)) {
      ChainTarget target = transferRotation.poll();
      if (target == null) {
        return;
      }
      if (!queuedTransfers.contains(target)) {
        continue;
      }
      HopperChain chain = chain(target);
      if (chain == null || !chain.fastPathEligible || chain.bypassedByPlayer || chain.length() < 3) {
        queuedTransfers.remove(target);
        continue;
      }
      transferRotation.offer(target);
      Object world = worldHandle(target.worldId);
      if (world != null) {
        synthesizeChainTransfer(world, target.worldId, chain, generation);
      }
    }
  }

  private void synthesizeChainTransfer(Object world, UUID worldId, HopperChain chain, long generation) {
    long head = chain.positions[0];
    long tail = chain.positions[chain.length() - 1];
    int hx = unpackX(head);
    int hy = unpackY(head);
    int hz = unpackZ(head);
    int tx = unpackX(tail);
    int ty = unpackY(tail);
    int tz = unpackZ(tail);
    int dx = modX(chain.direction);
    int dy = modY(chain.direction);
    int dz = modZ(chain.direction);
    J.runChunk(world, hx >> 4, hz >> 4, () -> {
      if (isActive(generation) && engaged) {
        applyChainTransfer(worldId, chain, hx, hy, hz, tx, ty, tz, dx, dy, dz);
      }
    });
  }

  void applyChainTransfer(
      UUID worldId,
      HopperChain chain,
      int hx,
      int hy,
      int hz,
      int tx,
      int ty,
      int tz,
      int dx,
      int dy,
      int dz
  ) {
    if (!featureBucketBypass) {
      chain.bucketPermit = false;
    }
    BlockHost blockHost = blocks;
    HopperHost host = hoppers;
    if (blockHost == null || host == null
        || !blockHost.isChunkLoaded(worldId, hx >> 4, hz >> 4) || !blockHost.isChunkLoaded(worldId, tx >> 4, tz >> 4)) {
      return;
    }
    if (!host.isOwnedByCurrentRegion(worldId, tx + dx, ty + dy, tz + dz)) {
      return;
    }
    GameLocation source = new GameLocation(worldId, hx, hy, hz);
    if (host.transferOne(worldId, hx, hy, hz, tx + dx, ty + dy, tz + dz, () -> tryConsumeFeatureBucket(source))) {
      chain.bucketPermit = true;
      synthesizedTransfers.incrementAndGet();
    }
  }

  private boolean tryConsumeFeatureBucket(GameLocation sourceLocation) {
    if (featureBucketBypass) {
      return true;
    }
    FeatureHopperTokenBucket tokenBucket = hopperTokenBucket;
    return tokenBucket == null || tokenBucket.tryConsume(sourceLocation);
  }

  private boolean requiresBucketPermit() {
    if (featureBucketBypass) {
      return false;
    }
    FeatureHopperTokenBucket tokenBucket = hopperTokenBucket;
    return tokenBucket != null && tokenBucket.isEnforcing();
  }

  private FeatureHopperTokenBucket resolveHopperTokenBucket() {
    return ReactComponents.feature(FeatureHopperTokenBucket.class);
  }

  private Object worldHandle(UUID worldId) {
    WorldHost worldHost = worlds;
    if (worldHost == null) {
      return null;
    }
    Optional<ReactWorld> world = worldHost.world(worldId);
    return world.map(ReactWorld::handle).orElse(null);
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
    int tickInterval = getTickInterval();
    tickCounter += Math.max(1L, tickInterval / 50L);
    enqueueMaintenanceRepairs();
    processCoordinateRepairs(generation);
    updateEngagement();
    if (!engaged) {
      return;
    }
    ticksSavedAccumulator.add(ticksSavedPerEvaluation.get() * tickInterval / 1000D);
    if (featureActMode && bridgeActive) {
      synthesizeChainTransfers(generation);
    }
  }

  public long chainsDetectedSnapshot() {
    return chainsDetected.get();
  }

  public double avgChainLengthSnapshot() {
    long count = chainsDetected.get();
    if (count <= 0L) {
      return 0D;
    }
    return chainLengthSum.get() / (double) count;
  }

  public double readAndResetTicksSaved() {
    return ticksSavedAccumulator.sumThenReset();
  }

  public long fastPathChainCountSnapshot() {
    return fastPathChainCount.get();
  }

  private void onPiston(BlockHost.PistonEvent event) {
    queueAffectedChunkRepairs(event.piston(), null);
    for (GameLocation block : event.blocks()) {
      queueAffectedChunkRepairs(block, null);
    }
  }

  void onChunkLoad(ChunkRef chunk) {
    queueRepair(new ChunkCoordinate(chunk.worldId(), chunk.chunkX(), chunk.chunkZ()), true);
  }

  void onChunkUnload(ChunkRef chunk) {
    removeChunkState(new ChunkCoordinate(chunk.worldId(), chunk.chunkX(), chunk.chunkZ()));
  }

  void onWorldUnload(UUID worldId) {
    removeWorldState(worldId);
  }

  void queueAffectedChunkRepairs(GameLocation block, String knownType) {
    if (block == null || !isRepairRelevant(block, knownType)) {
      return;
    }
    UUID worldId = block.worldId();
    int chunkX = block.blockX() >> 4;
    int chunkZ = block.blockZ() >> 4;
    for (int deltaX = -1; deltaX <= 1; deltaX++) {
      for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
        queueRepair(new ChunkCoordinate(worldId, chunkX + deltaX, chunkZ + deltaZ), true);
      }
    }
  }

  private boolean isRepairRelevant(GameLocation block, String knownType) {
    String material = knownType;
    if (material == null) {
      HopperHost host = hoppers;
      material = host == null ? null : host.blockType(block.worldId(), block.blockX(), block.blockY(), block.blockZ());
    }
    return "COMPARATOR".equals(material)
        || "REPEATER".equals(material)
        || CONTAINER_MATERIALS.contains(material);
  }

  private void queueRepair(ChunkCoordinate coordinate, boolean authoritative) {
    if (!active || coordinate == null || repairQueue == null || queuedRepairs == null) {
      return;
    }
    long now = System.currentTimeMillis();
    if (authoritative) {
      Long previous = rebuildDebounce.put(coordinate, now);
      if (previous != null && now - previous < CHAIN_REBUILD_DEBOUNCE_MS) {
        return;
      }
    }
    if (!queuedRepairs.add(coordinate)) {
      return;
    }
    if (!repairQueue.offer(coordinate)) {
      queuedRepairs.remove(coordinate);
      rebuildDebounce.remove(coordinate, now);
    }
  }

  private void enqueueMaintenanceRepairs() {
    BlockHost blockHost = blocks;
    if (blockHost == null) {
      return;
    }
    int budget = repairBudget();
    List<ChunkRef> targets = blockHost.nextLoadedChunks(budget);
    for (ChunkRef target : targets) {
      ChunkCoordinate coordinate = new ChunkCoordinate(target.worldId(), target.chunkX(), target.chunkZ());
      Long lastRepair = lastRepairTickByChunk.get(coordinate);
      if (lastRepair == null || tickCounter - lastRepair >= Math.max(1, rebuildIntervalTicks)) {
        queueRepair(coordinate, false);
      }
    }
  }

  private void processCoordinateRepairs(long generation) {
    int remaining = repairBudget();
    while (remaining-- > 0 && isActive(generation)) {
      ChunkCoordinate coordinate = repairQueue.poll();
      if (coordinate == null) {
        return;
      }
      if (!queuedRepairs.contains(coordinate)) {
        continue;
      }
      Object world = worldHandle(coordinate.worldId);
      if (world == null) {
        finishRepair(coordinate, false, generation);
        removeChunkState(coordinate);
        continue;
      }
      boolean scheduled = J.runChunk(world, coordinate.chunkX, coordinate.chunkZ, () -> {
        boolean completed = false;
        try {
          if (!isActive(generation)) {
            return;
          }
          BlockHost blockHost = blocks;
          if (worldHandle(coordinate.worldId) == null || blockHost == null
              || !blockHost.isChunkLoaded(coordinate.worldId, coordinate.chunkX, coordinate.chunkZ)) {
            removeChunkState(coordinate);
            return;
          }
          rebuildChunkChains(coordinate, generation);
          completed = true;
        } finally {
          finishRepair(coordinate, completed, generation);
        }
      });
      if (!scheduled) {
        finishRepair(coordinate, false, generation);
      }
    }
  }

  private void finishRepair(ChunkCoordinate coordinate, boolean completed, long generation) {
    if (generation != lifecycleGeneration.get()) {
      return;
    }
    queuedRepairs.remove(coordinate);
    rebuildDebounce.remove(coordinate);
    if (completed) {
      lastRepairTickByChunk.put(coordinate, tickCounter);
    }
  }

  private void rebuildChunkChains(ChunkCoordinate coordinate, long generation) {
    HopperHost host = hoppers;
    if (!isActive(generation) || host == null) {
      return;
    }
    Long2ObjectOpenHashMap<HopperNode> scannedNodes = new Long2ObjectOpenHashMap<>();
    Object chunk = host.chunk(coordinate.worldId, coordinate.chunkX, coordinate.chunkZ);
    if (chunk == null) {
      return;
    }
    for (HopperHost.HopperBlock hopper : host.hoppers(chunk)) {
      Facing facing = hopper.facing();
      if (facing == null || facing == Facing.UP) {
        continue;
      }
      scannedNodes.put(packPos(hopper.x(), hopper.y(), hopper.z()), new HopperNode(facing));
    }
    publishChunkNodes(coordinate, scannedNodes);
    rebuildChainsFromSnapshot(coordinate);
  }

  private void publishChunkNodes(
      ChunkCoordinate coordinate,
      Long2ObjectOpenHashMap<HopperNode> scannedNodes
  ) {
    Long2ObjectOpenHashMap<HopperNode> worldNodes = hopperNodesByWorld.computeIfAbsent(
        coordinate.worldId,
        ignored -> new Long2ObjectOpenHashMap<>()
    );
    long[] previousPositions = hopperPositionsByChunk.put(coordinate, scannedNodes.keySet().toLongArray());
    synchronized (worldNodes) {
      if (previousPositions != null) {
        for (long position : previousPositions) {
          worldNodes.remove(position);
        }
      }
      worldNodes.putAll(scannedNodes);
    }
  }

  private void rebuildChainsFromSnapshot(ChunkCoordinate coordinate) {
    removeChainsForChunk(coordinate.worldId, coordinate.chunkX, coordinate.chunkZ);
    long[] positions = hopperPositionsByChunk.get(coordinate);
    if (positions == null || positions.length == 0) {
      return;
    }
    Long2ObjectOpenHashMap<HopperNode> worldNodes = hopperNodesByWorld.get(coordinate.worldId);
    if (worldNodes == null) {
      return;
    }
    LongOpenHashSet visited = new LongOpenHashSet();
    synchronized (worldNodes) {
      for (long packed : positions) {
        if (visited.contains(packed)) {
          continue;
        }
        long headPacked = walkBackToChainHead(packed, worldNodes);
        if (visited.contains(headPacked)) {
          continue;
        }
        HopperChain chain = walkForwardFromHead(headPacked, worldNodes, visited);
        if (chain == null || chain.length() < Math.max(2, minChainLength)) {
          continue;
        }
        chain.fastPathEligible = computeFastPathEligible(coordinate.worldId, chain);
        chain.bypassedByPlayer = isChainNearPlayer(coordinate.worldId, chain);
        publishChain(coordinate.worldId, chain);
      }
    }
  }

  private long walkBackToChainHead(long packed, Long2ObjectOpenHashMap<HopperNode> hopperNodes) {
    long current = packed;
    int steps = 0;
    while (steps < MAX_CHAIN_LENGTH) {
      long upstream = findUpstreamHopper(current, hopperNodes);
      if (upstream == -1L) {
        return current;
      }
      current = upstream;
      steps++;
    }
    return current;
  }

  private long findUpstreamHopper(long packed, Long2ObjectOpenHashMap<HopperNode> hopperNodes) {
    int x = unpackX(packed);
    int y = unpackY(packed);
    int z = unpackZ(packed);
    long candidate = -1L;
    int candidateCount = 0;
    for (Facing face : UPSTREAM_FACES) {
      int nx = x + modX(face);
      int ny = y + modY(face);
      int nz = z + modZ(face);
      long npacked = packPos(nx, ny, nz);
      HopperNode upstream = hopperNodes.get(npacked);
      if (upstream == null) {
        continue;
      }
      Facing upstreamFacing = upstream.facing;
      int tx = nx + modX(upstreamFacing);
      int ty = ny + modY(upstreamFacing);
      int tz = nz + modZ(upstreamFacing);
      if (tx == x && ty == y && tz == z) {
        candidate = npacked;
        candidateCount++;
      }
    }
    if (candidateCount != 1) {
      return -1L;
    }
    return candidate;
  }

  private HopperChain walkForwardFromHead(
      long headPacked,
      Long2ObjectOpenHashMap<HopperNode> hopperNodes,
      LongOpenHashSet visited
  ) {
    long[] sequence = new long[MAX_CHAIN_LENGTH];
    int length = 0;
    long current = headPacked;
    Facing direction = null;
    while (length < MAX_CHAIN_LENGTH && hopperNodes.containsKey(current) && !visited.contains(current)) {
      sequence[length++] = current;
      visited.add(current);
      HopperNode currentNode = hopperNodes.get(current);
      if (currentNode == null) {
        break;
      }
      Facing facing = currentNode.facing;
      if (direction == null) {
        direction = facing;
      } else if (direction != facing) {
        break;
      }
      int nx = unpackX(current) + modX(facing);
      int ny = unpackY(current) + modY(facing);
      int nz = unpackZ(current) + modZ(facing);
      long nextPacked = packPos(nx, ny, nz);
      if (!hopperNodes.containsKey(nextPacked)) {
        break;
      }
      if (hasBranch(current, facing, hopperNodes)) {
        break;
      }
      current = nextPacked;
    }
    if (length < 2 || direction == null) {
      return null;
    }
    long[] trimmed = new long[length];
    System.arraycopy(sequence, 0, trimmed, 0, length);
    return new HopperChain(trimmed, direction);
  }

  private boolean hasBranch(
      long packed,
      Facing forward,
      Long2ObjectOpenHashMap<HopperNode> hopperNodes
  ) {
    int x = unpackX(packed);
    int y = unpackY(packed);
    int z = unpackZ(packed);
    int feedersIntoCurrent = 0;
    for (Facing face : UPSTREAM_FACES) {
      int nx = x + modX(face);
      int ny = y + modY(face);
      int nz = z + modZ(face);
      long npacked = packPos(nx, ny, nz);
      HopperNode neighbor = hopperNodes.get(npacked);
      if (neighbor == null) {
        continue;
      }
      Facing neighborFacing = neighbor.facing;
      int tx = nx + modX(neighborFacing);
      int ty = ny + modY(neighborFacing);
      int tz = nz + modZ(neighborFacing);
      if (tx == x && ty == y && tz == z && face != opposite(forward)) {
        feedersIntoCurrent++;
      }
    }
    return feedersIntoCurrent > 1;
  }

  private boolean computeFastPathEligible(UUID worldId, HopperChain chain) {
    if (chain.length() < Math.max(2, minChainLength)) {
      return false;
    }
    HopperHost host = hoppers;
    if (host == null) {
      return false;
    }
    long headPacked = chain.positions[0];
    int hx = unpackX(headPacked);
    int hy = unpackY(headPacked);
    int hz = unpackZ(headPacked);
    if (!host.isOwnedByCurrentRegion(worldId, hx, hy + 1, hz)) {
      return false;
    }
    String above = host.blockType(worldId, hx, hy + 1, hz);
    if ("COMPARATOR".equals(above) || "REPEATER".equals(above)) {
      return false;
    }
    long tailPacked = chain.positions[chain.length() - 1];
    int tx = unpackX(tailPacked) + modX(chain.direction);
    int ty = unpackY(tailPacked) + modY(chain.direction);
    int tz = unpackZ(tailPacked) + modZ(chain.direction);
    if (!host.isOwnedByCurrentRegion(worldId, tx, ty, tz)) {
      return false;
    }
    String tailTarget = host.blockType(worldId, tx, ty, tz);
    if ("HOPPER".equals(tailTarget) || "DROPPER".equals(tailTarget) || "DISPENSER".equals(tailTarget)) {
      return false;
    }
    return host.isContainer(worldId, tx, ty, tz);
  }

  private boolean isChainNearPlayer(UUID worldId, HopperChain chain) {
    long midPacked = chain.positions[chain.length() / 2];
    WorldHost worldHost = worlds;
    GameLocation midLocation = new GameLocation(worldId, unpackX(midPacked) + 0.5D, unpackY(midPacked) + 0.5D, unpackZ(midPacked) + 0.5D);
    return worldHost != null && bypassRadius > 0 && worldHost.anyPlayerNear(midLocation, bypassRadius);
  }

  private void publishChain(UUID worldId, HopperChain chain) {
    Long2ObjectOpenHashMap<HopperChain> chunkChains = chainsByWorldChunk.computeIfAbsent(
        worldId,
        ignored -> new Long2ObjectOpenHashMap<>()
    );
    synchronized (chunkChains) {
      HopperChain previous = chunkChains.put(chainKey(chain.positions[0]), chain);
      if (previous != null) {
        unregisterChain(worldId, previous);
      }
      registerChain(worldId, chain);
    }
  }

  private void registerChain(UUID worldId, HopperChain chain) {
    chainsDetected.incrementAndGet();
    chainLengthSum.addAndGet(chain.length());
    LongOpenHashSet touchedChunks = chain.touchedChunks();
    for (long chunkKey : touchedChunks) {
      ChunkCoordinate coordinate = new ChunkCoordinate(worldId, unpackChunkX(chunkKey), unpackChunkZ(chunkKey));
      chainHeadsByChunk.computeIfAbsent(coordinate, ignored -> ConcurrentHashMap.newKeySet())
          .add(chain.positions[0]);
    }
    if (!chain.fastPathEligible || chain.bypassedByPlayer || chain.length() < 3) {
      return;
    }
    fastPathChainCount.incrementAndGet();
    ticksSavedPerEvaluation.addAndGet(chain.length() - 1L);
    Long2ObjectOpenHashMap<HopperChain> mapping = middleHopperToChain.computeIfAbsent(
        worldId,
        ignored -> new Long2ObjectOpenHashMap<>()
    );
    synchronized (mapping) {
      for (int index = 1; index < chain.length() - 1; index++) {
        mapping.put(chain.positions[index], chain);
      }
    }
    ChainTarget target = new ChainTarget(worldId, chain.positions[0], chain);
    if (queuedTransfers.add(target) && !transferRotation.offer(target)) {
      queuedTransfers.remove(target);
    }
  }

  private void unregisterChain(UUID worldId, HopperChain chain) {
    chainsDetected.decrementAndGet();
    chainLengthSum.addAndGet(-chain.length());
    LongOpenHashSet touchedChunks = chain.touchedChunks();
    for (long chunkKey : touchedChunks) {
      ChunkCoordinate coordinate = new ChunkCoordinate(worldId, unpackChunkX(chunkKey), unpackChunkZ(chunkKey));
      Set<Long> heads = chainHeadsByChunk.get(coordinate);
      if (heads != null) {
        heads.remove(chain.positions[0]);
        if (heads.isEmpty()) {
          chainHeadsByChunk.remove(coordinate, heads);
        }
      }
    }
    if (chain.fastPathEligible && !chain.bypassedByPlayer && chain.length() >= 3) {
      fastPathChainCount.decrementAndGet();
      ticksSavedPerEvaluation.addAndGet(-(chain.length() - 1L));
    }
    Long2ObjectOpenHashMap<HopperChain> mapping = middleHopperToChain.get(worldId);
    if (mapping != null) {
      synchronized (mapping) {
        for (int index = 1; index < chain.length() - 1; index++) {
          if (mapping.get(chain.positions[index]) == chain) {
            mapping.remove(chain.positions[index]);
          }
        }
      }
    }
    queuedTransfers.remove(new ChainTarget(worldId, chain.positions[0], chain));
  }

  private void removeChainsForChunk(UUID worldId, int chunkX, int chunkZ) {
    ChunkCoordinate coordinate = new ChunkCoordinate(worldId, chunkX, chunkZ);
    Set<Long> indexedHeads = chainHeadsByChunk.remove(coordinate);
    if (indexedHeads == null || indexedHeads.isEmpty()) {
      return;
    }
    Long2ObjectOpenHashMap<HopperChain> chunkChains = chainsByWorldChunk.get(worldId);
    if (chunkChains == null) {
      return;
    }
    long touchKey = packChunk(chunkX, chunkZ);
    synchronized (chunkChains) {
      List<Long> heads = List.copyOf(indexedHeads);
      for (Long head : heads) {
        HopperChain chain = chunkChains.get(head.longValue());
        if (chain != null && chain.touchesChunk(touchKey)) {
          unregisterChain(worldId, chain);
          chunkChains.remove(head.longValue());
        }
      }
      if (chunkChains.isEmpty()) {
        chainsByWorldChunk.remove(worldId, chunkChains);
      }
    }
  }

  private HopperChain chain(ChainTarget target) {
    Long2ObjectOpenHashMap<HopperChain> chains = chainsByWorldChunk.get(target.worldId);
    if (chains == null) {
      return null;
    }
    synchronized (chains) {
      HopperChain chain = chains.get(target.headPosition);
      return chain == target.chain ? chain : null;
    }
  }

  private void removeChunkState(ChunkCoordinate coordinate) {
    if (coordinate == null || hopperPositionsByChunk == null) {
      return;
    }
    long[] positions = hopperPositionsByChunk.remove(coordinate);
    Long2ObjectOpenHashMap<HopperNode> worldNodes = hopperNodesByWorld.get(coordinate.worldId);
    if (positions != null && worldNodes != null) {
      synchronized (worldNodes) {
        for (long position : positions) {
          worldNodes.remove(position);
        }
        if (worldNodes.isEmpty()) {
          hopperNodesByWorld.remove(coordinate.worldId, worldNodes);
        }
      }
    }
    removeChainsForChunk(coordinate.worldId, coordinate.chunkX, coordinate.chunkZ);
    lastRepairTickByChunk.remove(coordinate);
    rebuildDebounce.remove(coordinate);
    queuedRepairs.remove(coordinate);
  }

  private void removeWorldState(UUID worldId) {
    Long2ObjectOpenHashMap<HopperChain> chains = chainsByWorldChunk.remove(worldId);
    if (chains != null) {
      synchronized (chains) {
        for (HopperChain chain : chains.values()) {
          unregisterChain(worldId, chain);
        }
        chains.clear();
      }
    }
    hopperNodesByWorld.remove(worldId);
    middleHopperToChain.remove(worldId);
    chainHeadsByChunk.keySet().removeIf(coordinate -> coordinate.worldId.equals(worldId));
    hopperPositionsByChunk.keySet().removeIf(coordinate -> coordinate.worldId.equals(worldId));
    lastRepairTickByChunk.keySet().removeIf(coordinate -> coordinate.worldId.equals(worldId));
    rebuildDebounce.keySet().removeIf(coordinate -> coordinate.worldId.equals(worldId));
    queuedRepairs.removeIf(coordinate -> coordinate.worldId.equals(worldId));
    queuedTransfers.removeIf(target -> target.worldId.equals(worldId));
  }

  private boolean isActive(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  private int repairBudget() {
    return Math.max(1, Math.min(repairChunksPerTick, MAX_COORDINATE_REPAIRS_PER_TICK));
  }

  private void updateEngagement() {
    double tickMs = sample(SamplerTickTime.ID);
    double incident = sample(SamplerIncidentScore.ID);
    if (!engaged) {
      if (tickMs >= engageOnTickMs || incident >= engageOnIncident) {
        engaged = true;
      }
      return;
    }
    if (tickMs <= releaseOnTickMs && incident < engageOnIncident) {
      engaged = false;
    }
  }

  private static long packChunk(int cx, int cz) {
    return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
  }

  private static int unpackChunkX(long packed) {
    return (int) (packed >> 32);
  }

  private static int unpackChunkZ(long packed) {
    return (int) packed;
  }

  private static long packPos(int x, int y, int z) {
    return ((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26) | (z & 0x3FFFFFF);
  }

  private static int unpackX(long packed) {
    return (int)(packed >> 38);
  }

  private static int unpackY(long packed) {
    int raw = (int)((packed >> 26) & 0xFFF);
    return (raw << 20) >> 20;
  }

  private static int unpackZ(long packed) {
    int raw = (int)(packed & 0x3FFFFFF);
    return (raw << 6) >> 6;
  }

  static int modX(Facing facing) {
    return facing == Facing.EAST ? 1 : facing == Facing.WEST ? -1 : 0;
  }

  static int modY(Facing facing) {
    return facing == Facing.UP ? 1 : facing == Facing.DOWN ? -1 : 0;
  }

  static int modZ(Facing facing) {
    return facing == Facing.SOUTH ? 1 : facing == Facing.NORTH ? -1 : 0;
  }

  static Facing opposite(Facing facing) {
    return switch (facing) {
      case NORTH -> Facing.SOUTH;
      case SOUTH -> Facing.NORTH;
      case EAST -> Facing.WEST;
      case WEST -> Facing.EAST;
      case UP -> Facing.DOWN;
      case DOWN -> Facing.UP;
    };
  }

  private static long chainKey(long headPacked) {
    return headPacked;
  }

  private record ChunkCoordinate(UUID worldId, int chunkX, int chunkZ) {
  }

  private record HopperNode(Facing facing) {
  }

  private record ChainTarget(UUID worldId, long headPosition, HopperChain chain) {
  }

  private static final class HopperChain {
    private final long[] positions;
    private final Facing direction;
    private volatile boolean fastPathEligible;
    private volatile boolean bypassedByPlayer;
    private volatile boolean bucketPermit;

    private HopperChain(long[] positions, Facing direction) {
      this.positions = positions;
      this.direction = direction;
    }

    private int length() {
      return positions.length;
    }

    private boolean touchesChunk(long chunkKey) {
      for (int i = 0; i < positions.length; i++) {
        int cx = unpackX(positions[i]) >> 4;
        int cz = unpackZ(positions[i]) >> 4;
        if (packChunk(cx, cz) == chunkKey) {
          return true;
        }
      }
      return false;
    }

    private LongOpenHashSet touchedChunks() {
      LongOpenHashSet chunks = new LongOpenHashSet();
      for (long position : positions) {
        chunks.add(packChunk(unpackX(position) >> 4, unpackZ(position) >> 4));
      }
      return chunks;
    }
  }
}
