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
import art.arcane.react.nms.HopperTickHook;
import art.arcane.react.nms.NmsBridge;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.nms.TickDecision;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Configuration for Hopper Chain Coalescing feature. Detects linear chains of N hoppers feeding the same direction and projects how many per-hopper transfer ticks would collapse into a single head-to-tail batched pull per chain per tick. Operates in measurement-only mode: chain detection plus projected ticks-saved exposed via the chain-coalescing sampler. Cancelling intermediate InventoryMoveItemEvent dispatches collides with FeatureHopperTokenBucket's bucket consumption ordering, so the actual batched move is deferred to a dedicated NMS bridge.")
public class FeatureHopperChainCoalescing extends ReactFeature implements Listener {
  public static final String ID = "hopper-chain-coalescing";
  private static final int MAX_CHAIN_LENGTH = 256;
  private static final int CHAIN_REBUILD_DEBOUNCE_MS = 250;

  @ConfigDoc(value = "Main evaluation interval for hopper chain coalescing in milliseconds.", impact = "Lower values rebuild stale chains faster but consume more CPU; higher values reduce overhead.")
  private int tickIntervalMS = 1000;
  @ConfigDoc(value = "Bypass radius around players in blocks; chains touching this radius skip coalescing accounting.", impact = "Higher values protect a wider zone around players; lower values count chains closer to players.")
  private int bypassRadius = 16;
  @ConfigDoc(value = "Minimum chain length to count as a coalesceable chain.", impact = "Higher values count only large sorting chains; lower values include shorter chains at extra accounting cost.")
  private int minChainLength = 4;
  @ConfigDoc(value = "Interval between full chain index rebuilds (ticks).", impact = "Higher values reuse existing chains longer but drift if block edits race the listeners; lower values rebuild more often.")
  private int rebuildIntervalTicks = 200;
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
  private transient Map<UUID, Long2LongOpenHashMap> chainHeadByHopper;
  private transient Map<UUID, Long2LongOpenHashMap> rebuildDebounce;
  private transient final AtomicLong chainsDetected = new AtomicLong(0L);
  private transient final AtomicLong chainLengthSum = new AtomicLong(0L);
  private transient final AtomicLong fastPathChainCount = new AtomicLong(0L);
  private transient final AtomicLong ticksSavedAccumulator = new AtomicLong(0L);
  private transient volatile long lastRebuildTick;
  private transient volatile long tickCounter;
  private transient volatile boolean engaged;
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
    chainsByWorldChunk = new ConcurrentHashMap<>();
    chainHeadByHopper = new ConcurrentHashMap<>();
    rebuildDebounce = new ConcurrentHashMap<>();
    chainsDetected.set(0L);
    chainLengthSum.set(0L);
    fastPathChainCount.set(0L);
    ticksSavedAccumulator.set(0L);
    skippedHopperTicks.set(0L);
    synthesizedTransfers.set(0L);
    middleHopperToChain.clear();
    lastRebuildTick = 0L;
    tickCounter = 0L;
    engaged = false;
    installBridgeHook();
  }

  @Override
  public void onDeactivate() {
    if (chainsByWorldChunk != null) {
      chainsByWorldChunk.clear();
    }
    if (chainHeadByHopper != null) {
      chainHeadByHopper.clear();
    }
    if (rebuildDebounce != null) {
      rebuildDebounce.clear();
    }
    middleHopperToChain.clear();
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

  private void publishMiddleMapping() {
    Map<UUID, Long2ObjectOpenHashMap<HopperChain>> next = new HashMap<>();
    for (Map.Entry<UUID, Long2ObjectOpenHashMap<HopperChain>> worldEntry : chainsByWorldChunk.entrySet()) {
      Long2ObjectOpenHashMap<HopperChain> mapping = next.computeIfAbsent(worldEntry.getKey(), ignored -> new Long2ObjectOpenHashMap<>());
      Long2ObjectOpenHashMap<HopperChain> chunkChains = worldEntry.getValue();
      synchronized (chunkChains) {
        Iterator<Long2ObjectOpenHashMap.Entry<HopperChain>> iterator = chunkChains.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
          HopperChain chain = iterator.next().getValue();
          if (!chain.fastPathEligible || chain.bypassedByPlayer || chain.length() < 3) {
            continue;
          }
          for (int i = 1; i < chain.length() - 1; i++) {
            mapping.put(chain.positions[i], chain);
          }
        }
      }
    }
    middleHopperToChain.clear();
    middleHopperToChain.putAll(next);
  }

  private void synthesizeChainTransfers() {
    for (Map.Entry<UUID, Long2ObjectOpenHashMap<HopperChain>> worldEntry : chainsByWorldChunk.entrySet()) {
      World world = Bukkit.getWorld(worldEntry.getKey());
      if (world == null) {
        continue;
      }
      Long2ObjectOpenHashMap<HopperChain> chunkChains = worldEntry.getValue();
      synchronized (chunkChains) {
        Iterator<Long2ObjectOpenHashMap.Entry<HopperChain>> iterator = chunkChains.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
          HopperChain chain = iterator.next().getValue();
          if (!chain.fastPathEligible || chain.bypassedByPlayer || chain.length() < 3) {
            continue;
          }
          synthesizeChainTransfer(world, chain);
        }
      }
    }
  }

  private void synthesizeChainTransfer(World world, HopperChain chain) {
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
    J.runChunk(world, hx >> 4, hz >> 4, () -> applyChainTransfer(world, hx, hy, hz, tx, ty, tz, dx, dy, dz));
  }

  private void applyChainTransfer(World world, int hx, int hy, int hz, int tx, int ty, int tz, int dx, int dy, int dz) {
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
      synthesizedTransfers.incrementAndGet();
    }
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
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    long featureTickServerTicks = Math.max(1L, tickIntervalMS / 50L);
    tickCounter += featureTickServerTicks;
    updateEngagement();
    if (!engaged) {
      pruneStaleChains();
      if (featureActMode && bridgeActive) {
        middleHopperToChain.clear();
      }
      return;
    }
    if (tickCounter - lastRebuildTick >= Math.max(1, rebuildIntervalTicks)) {
      lastRebuildTick = tickCounter;
      J.s(this::rebuildAllChains);
    } else {
      accumulateTicksSaved();
      pruneStaleChains();
    }
    if (featureActMode && bridgeActive) {
      publishMiddleMapping();
      synthesizeChainTransfers();
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
    if (block.getType() != Material.HOPPER && !isPotentialHopperNeighbor(block)) {
      return;
    }
    scheduleChunkRebuild(block);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (block.getType() != Material.HOPPER && !isPotentialHopperNeighbor(block)) {
      return;
    }
    scheduleChunkRebuild(block);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonExtendEvent event) {
    scheduleChunkRebuild(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonRetractEvent event) {
    scheduleChunkRebuild(event.getBlock());
  }

  private boolean isPotentialHopperNeighbor(Block block) {
    World world = block.getWorld();
    int x = block.getX();
    int y = block.getY();
    int z = block.getZ();
    if (worldHasHopperAt(world, x + 1, y, z)) {
      return true;
    }
    if (worldHasHopperAt(world, x - 1, y, z)) {
      return true;
    }
    if (worldHasHopperAt(world, x, y + 1, z)) {
      return true;
    }
    if (worldHasHopperAt(world, x, y - 1, z)) {
      return true;
    }
    if (worldHasHopperAt(world, x, y, z + 1)) {
      return true;
    }
    return worldHasHopperAt(world, x, y, z - 1);
  }

  private boolean worldHasHopperAt(World world, int x, int y, int z) {
    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
      return false;
    }
    return world.getBlockAt(x, y, z).getType() == Material.HOPPER;
  }

  private void scheduleChunkRebuild(Block block) {
    if (chainsByWorldChunk == null || rebuildDebounce == null) {
      return;
    }
    World world = block.getWorld();
    if (world == null) {
      return;
    }
    UUID worldId = world.getUID();
    long chunkKey = packChunk(block.getX() >> 4, block.getZ() >> 4);
    long now = System.currentTimeMillis();
    Long2LongOpenHashMap worldDebounce = rebuildDebounce.computeIfAbsent(worldId, ignored -> {
      Long2LongOpenHashMap created = new Long2LongOpenHashMap();
      created.defaultReturnValue(Long.MIN_VALUE);
      return created;
    });
    synchronized (worldDebounce) {
      long last = worldDebounce.get(chunkKey);
      if (last != Long.MIN_VALUE && now - last < CHAIN_REBUILD_DEBOUNCE_MS) {
        return;
      }
      worldDebounce.put(chunkKey, now);
    }
    rebuildChunkChains(world, block.getX() >> 4, block.getZ() >> 4);
  }

  private void rebuildAllChains() {
    for (World world : Bukkit.getWorlds()) {
      UUID worldId = world.getUID();
      Long2ObjectOpenHashMap<HopperChain> existing = chainsByWorldChunk.get(worldId);
      if (existing != null) {
        synchronized (existing) {
          existing.clear();
        }
      }
      Long2LongOpenHashMap headMap = chainHeadByHopper.get(worldId);
      if (headMap != null) {
        synchronized (headMap) {
          headMap.clear();
        }
      }
      for (Chunk chunk : world.getLoadedChunks()) {
        rebuildChunkChains(world, chunk.getX(), chunk.getZ());
      }
    }
    accumulateTicksSaved();
  }

  private void rebuildChunkChains(World world, int chunkX, int chunkZ) {
    UUID worldId = world.getUID();
    LongOpenHashSet hopperPositions = collectChunkHopperPositions(world, chunkX, chunkZ);
    if (hopperPositions.isEmpty()) {
      removeChainsForChunk(worldId, chunkX, chunkZ);
      return;
    }

    Long2ObjectOpenHashMap<HopperChain> chunkChains = chainsByWorldChunk
        .computeIfAbsent(worldId, ignored -> new Long2ObjectOpenHashMap<>());
    Long2LongOpenHashMap headByHopper = chainHeadByHopper
        .computeIfAbsent(worldId, ignored -> {
          Long2LongOpenHashMap created = new Long2LongOpenHashMap();
          created.defaultReturnValue(Long.MIN_VALUE);
          return created;
        });

    long chunkKey = packChunk(chunkX, chunkZ);
    synchronized (chunkChains) {
      Iterator<Long2ObjectOpenHashMap.Entry<HopperChain>> iterator = chunkChains.long2ObjectEntrySet().fastIterator();
      while (iterator.hasNext()) {
        Long2ObjectOpenHashMap.Entry<HopperChain> entry = iterator.next();
        HopperChain chain = entry.getValue();
        if (chain.touchesChunk(chunkKey)) {
          synchronized (headByHopper) {
            for (long pos : chain.positions) {
              headByHopper.remove(pos);
            }
          }
          iterator.remove();
        }
      }
    }

    LongOpenHashSet visited = new LongOpenHashSet();
    LongOpenHashSet allHoppers = collectNeighborhoodHoppers(world, chunkX, chunkZ);
    long[] positions = hopperPositions.toLongArray();
    for (int i = 0; i < positions.length; i++) {
      long packed = positions[i];
      if (visited.contains(packed)) {
        continue;
      }
      long headPacked = walkBackToChainHead(world, packed, allHoppers);
      if (visited.contains(headPacked)) {
        continue;
      }
      HopperChain chain = walkForwardFromHead(world, headPacked, allHoppers, visited);
      if (chain == null || chain.length() < Math.max(2, minChainLength)) {
        continue;
      }
      chain.fastPathEligible = computeFastPathEligible(world, chain);
      chain.bypassedByPlayer = isChainNearPlayer(world, chain);
      synchronized (chunkChains) {
        chunkChains.put(chainKey(chain.positions[0]), chain);
      }
      synchronized (headByHopper) {
        for (long pos : chain.positions) {
          headByHopper.put(pos, chain.positions[0]);
        }
      }
    }
  }

  private LongOpenHashSet collectChunkHopperPositions(World world, int chunkX, int chunkZ) {
    LongOpenHashSet result = new LongOpenHashSet();
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return result;
    }
    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
    addHopperPositions(chunk, result);
    return result;
  }

  private LongOpenHashSet collectNeighborhoodHoppers(World world, int chunkX, int chunkZ) {
    LongOpenHashSet result = new LongOpenHashSet();
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        int cx = chunkX + dx;
        int cz = chunkZ + dz;
        if (!world.isChunkLoaded(cx, cz)) {
          continue;
        }
        addHopperPositions(world.getChunkAt(cx, cz), result);
      }
    }
    return result;
  }

  private void addHopperPositions(Chunk chunk, LongOpenHashSet result) {
    for (BlockState state : chunk.getTileEntities()) {
      if (state instanceof Hopper) {
        Location loc = state.getLocation();
        result.add(packPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
      }
    }
  }

  private long walkBackToChainHead(World world, long packed, LongOpenHashSet hopperPositions) {
    long current = packed;
    int steps = 0;
    while (steps < MAX_CHAIN_LENGTH) {
      long upstream = findUpstreamHopper(world, current, hopperPositions);
      if (upstream == -1L) {
        return current;
      }
      current = upstream;
      steps++;
    }
    return current;
  }

  private long findUpstreamHopper(World world, long packed, LongOpenHashSet hopperPositions) {
    int x = unpackX(packed);
    int y = unpackY(packed);
    int z = unpackZ(packed);
    long candidate = -1L;
    int candidateCount = 0;
    BlockFace[] faces = new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP};
    for (int i = 0; i < faces.length; i++) {
      BlockFace face = faces[i];
      int nx = x + face.getModX();
      int ny = y + face.getModY();
      int nz = z + face.getModZ();
      long npacked = packPos(nx, ny, nz);
      if (!hopperPositions.contains(npacked)) {
        continue;
      }
      BlockFace upstreamFacing = hopperFacing(world, nx, ny, nz);
      if (upstreamFacing == null) {
        continue;
      }
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

  private HopperChain walkForwardFromHead(World world, long headPacked, LongOpenHashSet hopperPositions, LongOpenHashSet visited) {
    long[] sequence = new long[MAX_CHAIN_LENGTH];
    int length = 0;
    long current = headPacked;
    BlockFace direction = null;
    while (length < MAX_CHAIN_LENGTH && hopperPositions.contains(current) && !visited.contains(current)) {
      sequence[length++] = current;
      visited.add(current);
      BlockFace facing = hopperFacing(world, unpackX(current), unpackY(current), unpackZ(current));
      if (facing == null) {
        break;
      }
      if (direction == null) {
        direction = facing;
      } else if (direction != facing) {
        break;
      }
      int nx = unpackX(current) + facing.getModX();
      int ny = unpackY(current) + facing.getModY();
      int nz = unpackZ(current) + facing.getModZ();
      long nextPacked = packPos(nx, ny, nz);
      if (!hopperPositions.contains(nextPacked)) {
        break;
      }
      if (hasBranch(world, current, facing, hopperPositions)) {
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

  private boolean hasBranch(World world, long packed, BlockFace forward, LongOpenHashSet hopperPositions) {
    int x = unpackX(packed);
    int y = unpackY(packed);
    int z = unpackZ(packed);
    BlockFace[] faces = new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP};
    int feedersIntoCurrent = 0;
    for (int i = 0; i < faces.length; i++) {
      BlockFace face = faces[i];
      int nx = x + face.getModX();
      int ny = y + face.getModY();
      int nz = z + face.getModZ();
      long npacked = packPos(nx, ny, nz);
      if (!hopperPositions.contains(npacked)) {
        continue;
      }
      BlockFace neighborFacing = hopperFacing(world, nx, ny, nz);
      if (neighborFacing == null) {
        continue;
      }
      int tx = nx + neighborFacing.getModX();
      int ty = ny + neighborFacing.getModY();
      int tz = nz + neighborFacing.getModZ();
      if (tx == x && ty == y && tz == z && face != forward.getOppositeFace()) {
        feedersIntoCurrent++;
      }
    }
    return feedersIntoCurrent > 1;
  }

  private BlockFace hopperFacing(World world, int x, int y, int z) {
    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
      return null;
    }
    Block block = world.getBlockAt(x, y, z);
    if (block.getType() != Material.HOPPER) {
      return null;
    }
    BlockData data = block.getBlockData();
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
    Block above = world.getBlockAt(hx, hy + 1, hz);
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
    Block tailTarget = world.getBlockAt(tx + dx, ty + dy, tz + dz);
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

  private void accumulateTicksSaved() {
    long chains = 0L;
    long lengthSum = 0L;
    long fastPath = 0L;
    long ticksSaved = 0L;
    for (Map.Entry<UUID, Long2ObjectOpenHashMap<HopperChain>> worldEntry : chainsByWorldChunk.entrySet()) {
      Long2ObjectOpenHashMap<HopperChain> chunkChains = worldEntry.getValue();
      synchronized (chunkChains) {
        Iterator<Long2ObjectOpenHashMap.Entry<HopperChain>> iterator = chunkChains.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
          HopperChain chain = iterator.next().getValue();
          chains++;
          lengthSum += chain.length();
          if (chain.fastPathEligible && !chain.bypassedByPlayer) {
            fastPath++;
            ticksSaved += chain.length() - 1L;
          }
        }
      }
    }
    chainsDetected.set(chains);
    chainLengthSum.set(lengthSum);
    fastPathChainCount.set(fastPath);
    ticksSavedAccumulator.addAndGet(ticksSaved);
  }

  private void removeChainsForChunk(UUID worldId, int chunkX, int chunkZ) {
    Long2ObjectOpenHashMap<HopperChain> chunkChains = chainsByWorldChunk.get(worldId);
    if (chunkChains == null) {
      return;
    }
    long touchKey = packChunk(chunkX, chunkZ);
    Long2LongOpenHashMap headByHopper = chainHeadByHopper.get(worldId);
    synchronized (chunkChains) {
      Iterator<Long2ObjectOpenHashMap.Entry<HopperChain>> iterator = chunkChains.long2ObjectEntrySet().fastIterator();
      while (iterator.hasNext()) {
        Long2ObjectOpenHashMap.Entry<HopperChain> entry = iterator.next();
        HopperChain chain = entry.getValue();
        if (chain.touchesChunk(touchKey)) {
          if (headByHopper != null) {
            synchronized (headByHopper) {
              for (long pos : chain.positions) {
                headByHopper.remove(pos);
              }
            }
          }
          iterator.remove();
        }
      }
    }
  }

  private void pruneStaleChains() {
    Iterator<Map.Entry<UUID, Long2ObjectOpenHashMap<HopperChain>>> worldIterator = chainsByWorldChunk.entrySet().iterator();
    while (worldIterator.hasNext()) {
      Map.Entry<UUID, Long2ObjectOpenHashMap<HopperChain>> entry = worldIterator.next();
      Long2ObjectOpenHashMap<HopperChain> chunkChains = entry.getValue();
      synchronized (chunkChains) {
        if (chunkChains.isEmpty()) {
          worldIterator.remove();
        }
      }
    }
    Iterator<Map.Entry<UUID, Long2LongOpenHashMap>> debounceIterator = rebuildDebounce.entrySet().iterator();
    long now = System.currentTimeMillis();
    while (debounceIterator.hasNext()) {
      Map.Entry<UUID, Long2LongOpenHashMap> entry = debounceIterator.next();
      Long2LongOpenHashMap worldDebounce = entry.getValue();
      synchronized (worldDebounce) {
        Iterator<Long2LongMap.Entry> chunkIterator = worldDebounce.long2LongEntrySet().fastIterator();
        while (chunkIterator.hasNext()) {
          Long2LongMap.Entry chunkEntry = chunkIterator.next();
          if (now - chunkEntry.getLongValue() > Math.max(CHAIN_REBUILD_DEBOUNCE_MS, 60_000L)) {
            chunkIterator.remove();
          }
        }
        if (worldDebounce.isEmpty()) {
          debounceIterator.remove();
        }
      }
    }
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

  private double sample(String id) {
    try {
      art.arcane.react.api.sampler.Sampler sampler = React.sampler(id);
      return sampler == null ? 0D : sampler.sample();
    } catch (Throwable ignored) {
      return 0D;
    }
  }

  private static long packChunk(int cx, int cz) {
    return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
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

  private static final class HopperChain {
    private final long[] positions;
    private final BlockFace direction;
    private volatile boolean fastPathEligible;
    private volatile boolean bypassedByPlayer;

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
  }
}
