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
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.ObserverController.LoadedChunkTarget;
import art.arcane.react.nms.HopperTickHook;
import art.arcane.react.nms.NmsBridge;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.nms.TickDecision;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Configuration for Hopper Chain Coalescing feature. Detects linear hopper chains and reports projected tick savings. It remains measurement-only by default; in act mode with the versioned hopper hook, eligible intermediate ticks are skipped and one head-to-tail transfer is synthesized. Synthesized transfers coordinate with FeatureHopperTokenBucket gating instead of cancelling InventoryMoveItemEvent directly.")
public class FeatureHopperChainCoalescing extends ReactFeature implements Listener {
  public static final String ID = "hopper-chain-coalescing";
  private static final int MAX_CHAIN_LENGTH = 256;
  private static final int CHAIN_REBUILD_DEBOUNCE_MS = 250;
  private static final int MAX_PENDING_REPAIRS = 8192;
  private static final int MAX_PENDING_TRANSFERS = 8192;
  private static final int MAX_COORDINATE_REPAIRS_PER_TICK = 256;
  private static final int MAX_CHAIN_TRANSFERS_PER_TICK = 128;
  private static final BlockFace[] UPSTREAM_FACES = {
      BlockFace.NORTH,
      BlockFace.SOUTH,
      BlockFace.EAST,
      BlockFace.WEST,
      BlockFace.UP
  };

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
  private transient final AtomicLong ticksSavedAccumulator = new AtomicLong(0L);
  private transient final AtomicLong ticksSavedPerEvaluation = new AtomicLong(0L);
  private transient volatile long tickCounter;
  private transient volatile boolean engaged;
  private transient volatile boolean active;
  private transient volatile FeatureHopperTokenBucket hopperTokenBucket;
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
    ticksSavedAccumulator.set(0L);
    ticksSavedPerEvaluation.set(0L);
    skippedHopperTicks.set(0L);
    synthesizedTransfers.set(0L);
    middleHopperToChain.clear();
    tickCounter = 0L;
    engaged = false;
    hopperTokenBucket = resolveHopperTokenBucket();
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
    uninstallBridgeHook();
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
    if (!featureActMode) {
      bridgeActive = false;
      return;
    }
    NmsBridge bridge = NmsBridges.get();
    if (bridge == null) {
      bridgeActive = false;
      return;
    }
    HopperTickHook hook = (World world, int x, int y, int z) -> {
      if (!engaged) {
        return TickDecision.RUN_VANILLA;
      }
      UUID worldId = world.getUID();
      Long2ObjectOpenHashMap<HopperChain> mapping = middleHopperToChain.get(worldId);
      if (mapping == null) {
        return TickDecision.RUN_VANILLA;
      }
      long packed = packPos(x, y, z);
      HopperChain chain;
      synchronized (mapping) {
        chain = mapping.get(packed);
      }
      if (chain == null || !chain.fastPathEligible || chain.bypassedByPlayer) {
        return TickDecision.RUN_VANILLA;
      }
      if (chain.length() < 3) {
        return TickDecision.RUN_VANILLA;
      }
      if (requiresBucketPermit() && !chain.bucketPermit) {
        return TickDecision.RUN_VANILLA;
      }
      long head = chain.positions[0];
      long tail = chain.positions[chain.length() - 1];
      if (packed == head || packed == tail) {
        return TickDecision.RUN_VANILLA;
      }
      skippedHopperTicks.incrementAndGet();
      return TickDecision.SKIP;
    };
    bridgeActive = bridge.installHopperTickHook(hook);
  }

  private void uninstallBridgeHook() {
    NmsBridge bridge = NmsBridges.get();
    if (bridge != null) {
      bridge.uninstallHopperTickHook();
    }
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
      World world = Bukkit.getWorld(target.worldId);
      if (world != null) {
        synthesizeChainTransfer(world, chain, generation);
      }
    }
  }

  private void synthesizeChainTransfer(World world, HopperChain chain, long generation) {
    long head = chain.positions[0];
    long tail = chain.positions[chain.length() - 1];
    int hx = unpackX(head);
    int hy = unpackY(head);
    int hz = unpackZ(head);
    int tx = unpackX(tail);
    int ty = unpackY(tail);
    int tz = unpackZ(tail);
    int dx = chain.direction.getModX();
    int dy = chain.direction.getModY();
    int dz = chain.direction.getModZ();
    J.runChunk(world, hx >> 4, hz >> 4, () -> {
      if (isActive(generation) && engaged) {
        applyChainTransfer(world, chain, hx, hy, hz, tx, ty, tz, dx, dy, dz);
      }
    });
  }

  private void applyChainTransfer(
      World world,
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
    if (!world.isChunkLoaded(hx >> 4, hz >> 4) || !world.isChunkLoaded(tx >> 4, tz >> 4)) {
      return;
    }
    Location tailRecipientLocation = new Location(world, tx + dx, ty + dy, tz + dz);
    if (!J.isOwnedByCurrentRegion(tailRecipientLocation)) {
      return;
    }
    Inventory headInv = inventoryAt(world, hx, hy, hz);
    if (headInv == null || headInv.isEmpty()) {
      return;
    }
    Inventory tailRecipient = inventoryAt(world, tx + dx, ty + dy, tz + dz);
    if (tailRecipient == null) {
      return;
    }
    ItemStack[] contents = headInv.getStorageContents();
    int moved = 0;
    for (int slot = 0; slot < contents.length; slot++) {
      ItemStack stack = contents[slot];
      if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
        continue;
      }
      ItemStack transfer = stack.clone();
      transfer.setAmount(1);
      if (!tryConsumeFeatureBucket(new Location(world, hx, hy, hz))) {
        return;
      }
      Map<Integer, ItemStack> overflow = tailRecipient.addItem(transfer);
      if (!overflow.isEmpty()) {
        break;
      }
      stack.setAmount(stack.getAmount() - 1);
      if (stack.getAmount() <= 0) {
        contents[slot] = null;
      }
      moved++;
      break;
    }
    if (moved > 0) {
      headInv.setStorageContents(contents);
      chain.bucketPermit = true;
      synthesizedTransfers.incrementAndGet();
    }
  }

  private boolean tryConsumeFeatureBucket(Location sourceLocation) {
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
    if (React.instance == null) {
      return null;
    }
    FeatureController controller = React.controller(FeatureController.class);
    if (controller == null || controller.getFeatures() == null) {
      return null;
    }
    return controller.getFeatures().get(FeatureHopperTokenBucket.class);
  }

  private Inventory inventoryAt(World world, int x, int y, int z) {
    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
      return null;
    }
    Block block = world.getBlockAt(x, y, z);
    BlockState state = block.getState();
    if (state instanceof InventoryHolder holder) {
      return holder.getInventory();
    }
    return null;
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
    long featureTickServerTicks = Math.max(1L, tickIntervalMS / 50L);
    tickCounter += featureTickServerTicks;
    enqueueMaintenanceRepairs();
    processCoordinateRepairs(generation);
    updateEngagement();
    if (!engaged) {
      return;
    }
    ticksSavedAccumulator.addAndGet(ticksSavedPerEvaluation.get());
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

  public long readAndResetTicksSaved() {
    return ticksSavedAccumulator.getAndSet(0L);
  }

  public long fastPathChainCountSnapshot() {
    return fastPathChainCount.get();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent event) {
    Block block = event.getBlock();
    queueAffectedChunkRepairs(block);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent event) {
    Block block = event.getBlock();
    queueAffectedChunkRepairs(block);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonExtendEvent event) {
    queueAffectedChunkRepairs(event.getBlock());
    for (Block block : event.getBlocks()) {
      queueAffectedChunkRepairs(block);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonRetractEvent event) {
    queueAffectedChunkRepairs(event.getBlock());
    for (Block block : event.getBlocks()) {
      queueAffectedChunkRepairs(block);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkLoadEvent event) {
    queueRepair(ChunkCoordinate.of(event.getChunk()), true);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkUnloadEvent event) {
    removeChunkState(ChunkCoordinate.of(event.getChunk()));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(WorldUnloadEvent event) {
    removeWorldState(event.getWorld().getUID());
  }

  private void queueAffectedChunkRepairs(Block block) {
    if (block == null || !isRepairRelevant(block)) {
      return;
    }
    World world = block.getWorld();
    UUID worldId = world.getUID();
    int chunkX = block.getX() >> 4;
    int chunkZ = block.getZ() >> 4;
    for (int deltaX = -1; deltaX <= 1; deltaX++) {
      for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
        queueRepair(new ChunkCoordinate(worldId, chunkX + deltaX, chunkZ + deltaZ), true);
      }
    }
  }

  private boolean isRepairRelevant(Block block) {
    Material material = block.getType();
    if (material == Material.HOPPER
        || material == Material.COMPARATOR
        || material == Material.REPEATER
        || material == Material.DROPPER
        || material == Material.DISPENSER) {
      return true;
    }
    return block.getState() instanceof InventoryHolder;
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
    if (React.instance == null) {
      return;
    }
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null) {
      return;
    }
    int budget = repairBudget();
    List<LoadedChunkTarget> targets = observer.nextLoadedChunkCoordinateBatch(budget);
    for (LoadedChunkTarget target : targets) {
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
      World world = Bukkit.getWorld(coordinate.worldId);
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
          World currentWorld = Bukkit.getWorld(coordinate.worldId);
          if (currentWorld == null || !currentWorld.isChunkLoaded(coordinate.chunkX, coordinate.chunkZ)) {
            removeChunkState(coordinate);
            return;
          }
          rebuildChunkChains(currentWorld, coordinate, generation);
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

  private void rebuildChunkChains(World world, ChunkCoordinate coordinate, long generation) {
    if (!isActive(generation)) {
      return;
    }
    Chunk chunk = world.getChunkAt(coordinate.chunkX, coordinate.chunkZ);
    Long2ObjectOpenHashMap<HopperNode> scannedNodes = new Long2ObjectOpenHashMap<>();
    for (BlockState state : chunk.getTileEntities()) {
      if (!(state instanceof Hopper)) {
        continue;
      }
      Location location = state.getLocation();
      BlockFace facing = hopperFacing(state.getBlockData());
      if (facing == null) {
        continue;
      }
      long position = packPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
      scannedNodes.put(position, new HopperNode(facing));
    }
    publishChunkNodes(coordinate, scannedNodes);
    rebuildChainsFromSnapshot(world, coordinate);
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

  private void rebuildChainsFromSnapshot(World world, ChunkCoordinate coordinate) {
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
        chain.fastPathEligible = computeFastPathEligible(world, chain);
        chain.bypassedByPlayer = isChainNearPlayer(world, chain);
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
    for (BlockFace face : UPSTREAM_FACES) {
      int nx = x + face.getModX();
      int ny = y + face.getModY();
      int nz = z + face.getModZ();
      long npacked = packPos(nx, ny, nz);
      HopperNode upstream = hopperNodes.get(npacked);
      if (upstream == null) {
        continue;
      }
      BlockFace upstreamFacing = upstream.facing;
      int tx = nx + upstreamFacing.getModX();
      int ty = ny + upstreamFacing.getModY();
      int tz = nz + upstreamFacing.getModZ();
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
    BlockFace direction = null;
    while (length < MAX_CHAIN_LENGTH && hopperNodes.containsKey(current) && !visited.contains(current)) {
      sequence[length++] = current;
      visited.add(current);
      HopperNode currentNode = hopperNodes.get(current);
      if (currentNode == null) {
        break;
      }
      BlockFace facing = currentNode.facing;
      if (direction == null) {
        direction = facing;
      } else if (direction != facing) {
        break;
      }
      int nx = unpackX(current) + facing.getModX();
      int ny = unpackY(current) + facing.getModY();
      int nz = unpackZ(current) + facing.getModZ();
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
      BlockFace forward,
      Long2ObjectOpenHashMap<HopperNode> hopperNodes
  ) {
    int x = unpackX(packed);
    int y = unpackY(packed);
    int z = unpackZ(packed);
    int feedersIntoCurrent = 0;
    for (BlockFace face : UPSTREAM_FACES) {
      int nx = x + face.getModX();
      int ny = y + face.getModY();
      int nz = z + face.getModZ();
      long npacked = packPos(nx, ny, nz);
      HopperNode neighbor = hopperNodes.get(npacked);
      if (neighbor == null) {
        continue;
      }
      BlockFace neighborFacing = neighbor.facing;
      int tx = nx + neighborFacing.getModX();
      int ty = ny + neighborFacing.getModY();
      int tz = nz + neighborFacing.getModZ();
      if (tx == x && ty == y && tz == z && face != forward.getOppositeFace()) {
        feedersIntoCurrent++;
      }
    }
    return feedersIntoCurrent > 1;
  }

  private BlockFace hopperFacing(BlockData data) {
    if (data instanceof org.bukkit.block.data.type.Hopper hopperData) {
      BlockFace facing = hopperData.getFacing();
      if (facing == BlockFace.DOWN || facing == BlockFace.NORTH || facing == BlockFace.SOUTH || facing == BlockFace.EAST || facing == BlockFace.WEST) {
        return facing;
      }
    }
    return null;
  }

  private boolean computeFastPathEligible(World world, HopperChain chain) {
    if (chain.length() < Math.max(2, minChainLength)) {
      return false;
    }
    long headPacked = chain.positions[0];
    int hx = unpackX(headPacked);
    int hy = unpackY(headPacked);
    int hz = unpackZ(headPacked);
    Location aboveLocation = new Location(world, hx, hy + 1, hz);
    if (!J.isOwnedByCurrentRegion(aboveLocation)) {
      return false;
    }
    Block above = world.getBlockAt(aboveLocation);
    if (above.getType() == Material.COMPARATOR || above.getType() == Material.REPEATER) {
      return false;
    }
    long tailPacked = chain.positions[chain.length() - 1];
    int tx = unpackX(tailPacked);
    int ty = unpackY(tailPacked);
    int tz = unpackZ(tailPacked);
    int dx = chain.direction.getModX();
    int dy = chain.direction.getModY();
    int dz = chain.direction.getModZ();
    Location tailTargetLocation = new Location(world, tx + dx, ty + dy, tz + dz);
    if (!J.isOwnedByCurrentRegion(tailTargetLocation)) {
      return false;
    }
    Block tailTarget = world.getBlockAt(tailTargetLocation);
    if (tailTarget.getType() == Material.HOPPER || tailTarget.getType() == Material.DROPPER || tailTarget.getType() == Material.DISPENSER) {
      return false;
    }
    return tailTarget.getState() instanceof InventoryHolder;
  }

  private boolean isChainNearPlayer(World world, HopperChain chain) {
    long midPacked = chain.positions[chain.length() / 2];
    Location midLocation = new Location(world, unpackX(midPacked) + 0.5D, unpackY(midPacked) + 0.5D, unpackZ(midPacked) + 0.5D);
    return React.hasNearbyPlayer(midLocation, bypassRadius);
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

  private static long chainKey(long headPacked) {
    return headPacked;
  }

  private record ChunkCoordinate(UUID worldId, int chunkX, int chunkZ) {
    private static ChunkCoordinate of(Chunk chunk) {
      return new ChunkCoordinate(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
  }

  private record HopperNode(BlockFace facing) {
  }

  private record ChainTarget(UUID worldId, long headPosition, HopperChain chain) {
  }

  private static final class HopperChain {
    private final long[] positions;
    private final BlockFace direction;
    private volatile boolean fastPathEligible;
    private volatile boolean bypassedByPlayer;
    private volatile boolean bucketPermit;

    private HopperChain(long[] positions, BlockFace direction) {
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
