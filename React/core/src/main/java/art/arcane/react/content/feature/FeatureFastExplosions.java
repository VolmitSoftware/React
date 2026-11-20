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
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.platform.ExplosionHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.math.M;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reduces entity spawns / garbage by teleporting drops and xp from blocks and
 * entities directly into your inventory
 */
@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Explosions feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureFastExplosions extends ReactFeature {
  public static final String ID = "fast-explosions";
  private static final int COUNTER_INTERVAL_MILLIS = 50;
  private static final int MAX_RETRY_BATCHES_PER_TICK = 64;
  private static final int MAX_BLOCKS_PER_OWNER_EXECUTION = 4096;
  private static final String TNT = "TNT";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum primes allowed per tick in fast explosions.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxPrimesPerTick = 3;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Fuse spread added between chained primed TNT entities in fast explosions (ticks).", impact = "Higher values stagger explosions more; lower values keep chain timing tighter.")
  private int spreadPrimedFuseTicks = 7;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum explosion chains allowed per tick in fast explosions.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxExplosionChainsPerTick = 3;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast explosions applies fast block updates.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean fastBlockUpdates = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast explosions applies disable entity chain reactions.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean disableEntityChainReactions = false;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast explosions applies explosion chain reactions.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean explosionChainReactions = false;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum time to drain accepted fast-explosion block work during deactivation (milliseconds).", impact = "A timeout fails deactivation instead of silently losing blocks already removed from vanilla explosion handling.")
  private int shutdownDrainTimeoutMS = 2000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum accepted explosion blocks retained across all worlds.", impact = "Lower values bound shutdown memory more tightly; overflow remains in vanilla explosion handling.")
  private int maxPendingBlocksGlobal = 16_384;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum accepted explosion blocks retained for one world.", impact = "Lower values prevent one world from consuming the whole global pending-block allowance.")
  private int maxPendingBlocksPerWorld = 8192;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum explosion blocks processed by one chunk-owner callback.", impact = "Lower values tighten region-tick work at the cost of more continuation ticks.")
  private int maxBlocksPerOwnerExecution = 128;
  private final transient FastExplosionWindow window = new FastExplosionWindow();
  private final transient Map<Long, ExplosionChunkBatch> pendingBatches = new ConcurrentHashMap<>();
  private final transient Map<UUID, Integer> pendingBlocksByWorld = new HashMap<>();
  private final transient AtomicLong batchSequence = new AtomicLong();
  private final transient Object pendingMonitor = new Object();
  private final transient Object admissionLock = new Object();
  private transient int pendingBlockCount;
  private transient volatile boolean accepting = true;
  private transient volatile ExplosionHost explosions;
  private transient volatile WorldHost worlds;
  private transient List<Subscription> subscriptions = List.of();

  public FeatureFastExplosions() {
    super(ID);
  }

  @Override
  public void onActivate() {
    accepting = true;
    ReactPlatform platform = ReactPlatforms.get();
    explosions = platform.explosionHost().orElse(null);
    worlds = platform.worldHost().orElse(null);
    ExplosionHost host = explosions;
    if (host != null) {
      subscriptions = List.of(
          host.onTntPrimed(ListenerPriority.HIGHEST, true, this::onPrimed),
          host.onExplode(ListenerPriority.HIGHEST, true, this::onExplode),
          host.onWorldUnload(ListenerPriority.HIGHEST, false, this::onWorldUnload)
      );
    }
    retryPendingBatches(MAX_RETRY_BATCHES_PER_TICK);
  }

  @Override
  public void onDeactivate() {
    accepting = false;
    for (Subscription subscription : subscriptions) {
      subscription.cancel();
    }
    subscriptions = List.of();
    boolean inlinePaperDrain = ReactPlatforms.getOrNull() != null
        && !J.isFoliaThreading()
        && J.isPrimaryThread();
    boolean haltedScheduler = J.isFoliaThreading() && J.isServerStopping();
    long timeoutMillis = Math.max(0L, shutdownDrainTimeoutMS);
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    while (!pendingBatches.isEmpty()) {
      boolean stalled = false;
      if (inlinePaperDrain) {
        drainPendingPaperBatches(MAX_RETRY_BATCHES_PER_TICK);
      } else if (haltedScheduler) {
        stalled = !drainBatchesOwnedByThisThread(MAX_RETRY_BATCHES_PER_TICK);
      } else {
        retryPendingBatches(MAX_RETRY_BATCHES_PER_TICK);
      }
      if (pendingBatches.isEmpty()) {
        return;
      }
      if (haltedScheduler && !stalled) {
        continue;
      }
      long remaining = stalled ? 0L : deadline - System.nanoTime();
      if (remaining <= 0L) {
        for (ExplosionChunkBatch batch : pendingBatches.values()) {
          batch.scheduled.set(false);
        }
        throw new IllegalStateException(
            "Fast explosions retained " + pendingBatches.size() + " unfinished owner batches"
        );
      }

      synchronized (pendingMonitor) {
        try {
          TimeUnit.NANOSECONDS.timedWait(
              pendingMonitor,
              Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(25L))
          );
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while draining fast explosions", exception);
        }
      }
    }
  }

  void onPrimed(ExplosionHost.PrimedTnt tnt) {
    int primeIndex = window.nextPrimeIndex();
    if (spreadPrimedFuseTicks > 0) {
      tnt.setFuseTicks((primeIndex * spreadPrimedFuseTicks) + tnt.fuseTicks());
    }
  }

  void onExplode(ExplosionHost.Explosion explosion) {
    if (!explosion.fromBlock() && !explosion.destroysBlocks()) {
      return;
    }

    processExplosion(explosion);
  }

  void onWorldUnload(ExplosionHost.WorldUnload event) {
    UUID world = event.worldId();
    if (!hasPendingAdmission(world)) {
      return;
    }

    event.cancel();
    retryPendingWorldBatches(world, MAX_RETRY_BATCHES_PER_TICK);
  }

  private void processExplosion(ExplosionHost.Explosion explosion) {
    List<ExplosionHost.ExplodedBlock> eventBlocks = explosion.blocks();
    if (!accepting || eventBlocks.isEmpty()) {
      return;
    }

    Set<GameLocation> vanillaPrimes = selectReservedPrimes(eventBlocks);
    Set<GameLocation> accepted = processExplosionBlocks(eventBlocks, vanillaPrimes, explosion.yield());
    if (accepted.isEmpty()) {
      return;
    }
    explosion.removeBlocks(block -> accepted.contains(block.location()) && !vanillaPrimes.contains(block.location()));
  }

  private Set<GameLocation> selectReservedPrimes(List<ExplosionHost.ExplodedBlock> blocks) {
    int maximumReserved = Math.max(0, maxPrimesPerTick);
    if (disableEntityChainReactions || maximumReserved == 0) {
      return Set.of();
    }

    Deque<GameLocation> lastPrimes = new ArrayDeque<>(maximumReserved);
    int requested = 0;
    for (ExplosionHost.ExplodedBlock block : blocks) {
      if (block == null || !TNT.equals(block.type())) {
        continue;
      }

      requested++;
      if (lastPrimes.size() == maximumReserved) {
        lastPrimes.removeFirst();
      }
      lastPrimes.addLast(block.location());
    }

    int reserved = window.reservePrimes(requested, maxPrimesPerTick);
    if (reserved <= 0) {
      return Set.of();
    }

    Set<GameLocation> selected = new HashSet<>(reserved);
    while (lastPrimes.size() > reserved) {
      lastPrimes.removeFirst();
    }
    selected.addAll(lastPrimes);
    return selected;
  }

  private Set<GameLocation> processExplosionBlocks(
      Iterable<ExplosionHost.ExplodedBlock> blocks,
      Set<GameLocation> vanillaPrimes,
      float yield
  ) {
    List<ExplosionWork> work = new ArrayList<>();
    int collectionLimit = Math.max(1, maxPendingBlocksGlobal);
    for (ExplosionHost.ExplodedBlock block : blocks) {
      if (work.size() >= collectionLimit) {
        break;
      }
      if (block == null) {
        continue;
      }

      GameLocation location = block.location();
      if (location == null || location.worldId() == null) {
        continue;
      }

      String type = block.type();
      boolean directTnt = TNT.equals(type)
          && explosionChainReactions
          && !vanillaPrimes.contains(location);
      boolean chainPermit = directTnt && window.tryAcquireExplosionChain(maxExplosionChainsPerTick);
      work.add(new ExplosionWork(location, type, directTnt, chainPermit));
    }

    if (work.isEmpty()) {
      return Set.of();
    }

    Set<GameLocation> accepted = new HashSet<>();
    if (!J.isFoliaThreading()) {
      ExplosionChunkBatch batch = new ExplosionChunkBatch(batchSequence.incrementAndGet(), work, yield);
      if (registerAndSchedule(batch)) {
        addAccepted(accepted, work);
      }
      return accepted;
    }

    Map<UUID, Long2ObjectLinkedOpenHashMap<ExplosionChunkBatch>> batchesByWorld = new LinkedHashMap<>();
    for (ExplosionWork blockWork : work) {
      GameLocation location = blockWork.location;
      Long2ObjectLinkedOpenHashMap<ExplosionChunkBatch> chunkBatches = batchesByWorld.computeIfAbsent(
          location.worldId(),
          ignored -> new Long2ObjectLinkedOpenHashMap<>());
      long chunkKey = packChunk(location.chunkX(), location.chunkZ());
      ExplosionChunkBatch batch = chunkBatches.get(chunkKey);
      if (batch == null) {
        chunkBatches.put(
            chunkKey,
            new ExplosionChunkBatch(batchSequence.incrementAndGet(), blockWork, yield)
        );
      } else {
        batch.work.addLast(blockWork);
      }
    }

    for (Long2ObjectLinkedOpenHashMap<ExplosionChunkBatch> chunkBatches : batchesByWorld.values()) {
      for (ExplosionChunkBatch batch : chunkBatches.values()) {
        if (registerAndSchedule(batch)) {
          addAccepted(accepted, batch.work);
        }
      }
    }
    return accepted;
  }

  private void addAccepted(Set<GameLocation> accepted, Iterable<ExplosionWork> work) {
    for (ExplosionWork blockWork : work) {
      accepted.add(blockWork.location);
    }
  }

  private boolean registerAndSchedule(ExplosionChunkBatch batch) {
    int admitted = reserveAdmission(batch.world, batch.work.size());
    if (admitted <= 0) {
      return false;
    }
    batch.retainFirst(admitted);
    pendingBatches.put(batch.id, batch);
    if (scheduleBatch(batch)) {
      return true;
    }

    pendingBatches.remove(batch.id, batch);
    releaseAdmission(batch.world, batch.work.size());
    signalPendingChange();
    return false;
  }

  private boolean scheduleBatch(ExplosionChunkBatch batch) {
    if (batch.executing.get() || !batch.scheduled.compareAndSet(false, true)) {
      return true;
    }

    boolean accepted;
    try {
      ReactWorld world = world(batch.world);
      accepted = world != null && J.runChunk(
          world,
          batch.anchor.chunkX(),
          batch.anchor.chunkZ(),
          () -> executeBatch(batch),
          1
      );
    } catch (RuntimeException | Error failure) {
      batch.scheduled.set(false);
      reportError(failure);
      return false;
    }
    if (!accepted) {
      batch.scheduled.set(false);
    }
    return accepted;
  }

  private ReactWorld world(UUID worldId) {
    WorldHost host = worlds;
    return host == null || worldId == null ? null : host.world(worldId).orElse(null);
  }

  private void executeBatch(ExplosionChunkBatch batch) {
    if (pendingBatches.get(batch.id) != batch || !batch.executing.compareAndSet(false, true)) {
      return;
    }

    batch.scheduled.set(false);
    int completed = 0;
    int sliceLimit = Math.max(1, Math.min(maxBlocksPerOwnerExecution, MAX_BLOCKS_PER_OWNER_EXECUTION));
    List<ExplosionWork> retry = new ArrayList<>();
    for (int i = 0; i < sliceLimit; i++) {
      ExplosionWork blockWork = batch.work.pollFirst();
      if (blockWork == null) {
        break;
      }

      try {
        processExplosionBlock(blockWork, batch.yield);
        completed++;
      } catch (Throwable throwable) {
        reportError(throwable);
        retry.add(blockWork);
      }
    }
    batch.work.addAll(retry);
    if (completed > 0) {
      releaseAdmission(batch.world, completed);
    }
    batch.executing.set(false);
    if (batch.work.isEmpty()) {
      pendingBatches.remove(batch.id, batch);
    }
    signalPendingChange();
  }

  private void retryPendingBatches(int maximum) {
    int remaining = Math.max(0, maximum);
    for (ExplosionChunkBatch batch : pendingBatches.values()) {
      if (remaining-- <= 0) {
        return;
      }
      scheduleBatch(batch);
    }
  }

  private void retryPendingWorldBatches(UUID world, int maximum) {
    int remaining = Math.max(0, maximum);
    for (ExplosionChunkBatch batch : pendingBatches.values()) {
      if (remaining <= 0) {
        return;
      }
      if (!batch.world.equals(world)) {
        continue;
      }
      remaining--;
      scheduleBatch(batch);
    }
  }

  private void drainPendingPaperBatches(int maximum) {
    int remaining = Math.max(0, maximum);
    for (ExplosionChunkBatch batch : pendingBatches.values()) {
      if (remaining-- <= 0) {
        return;
      }
      executeBatch(batch);
    }
  }

  /**
   * Runs batches whose chunk this thread may touch, for a stopping server where queued chunk
   * tasks never run. Returns whether any batch got shorter, so a caller can stop retrying.
   */
  private boolean drainBatchesOwnedByThisThread(int maximum) {
    boolean progressed = false;
    int remaining = Math.max(0, maximum);
    for (ExplosionChunkBatch batch : pendingBatches.values()) {
      if (remaining <= 0) {
        break;
      }
      if (!ownedByCurrentRegion(batch)) {
        continue;
      }

      remaining--;
      int queued = batch.work.size();
      executeBatch(batch);
      progressed |= batch.work.size() < queued;
    }
    return progressed;
  }

  private boolean ownedByCurrentRegion(ExplosionChunkBatch batch) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    ReactWorld world = world(batch.world);
    return platform != null && world != null
        && platform.scheduler().isOwnedByCurrentRegion(world, batch.anchor.chunkX(), batch.anchor.chunkZ());
  }

  private void signalPendingChange() {
    synchronized (pendingMonitor) {
      pendingMonitor.notifyAll();
    }
  }

  private int reserveAdmission(UUID world, int requested) {
    if (world == null || requested <= 0) {
      return 0;
    }

    synchronized (admissionLock) {
      int globalLimit = Math.max(1, maxPendingBlocksGlobal);
      int worldLimit = Math.max(1, maxPendingBlocksPerWorld);
      int worldPending = pendingBlocksByWorld.getOrDefault(world, 0);
      int admitted = Math.min(
          requested,
          Math.min(globalLimit - pendingBlockCount, worldLimit - worldPending)
      );
      if (admitted <= 0) {
        return 0;
      }

      pendingBlockCount += admitted;
      pendingBlocksByWorld.put(world, worldPending + admitted);
      return admitted;
    }
  }

  private void releaseAdmission(UUID world, int completed) {
    if (world == null || completed <= 0) {
      return;
    }

    synchronized (admissionLock) {
      pendingBlockCount = Math.max(0, pendingBlockCount - completed);
      int remaining = Math.max(0, pendingBlocksByWorld.getOrDefault(world, 0) - completed);
      if (remaining == 0) {
        pendingBlocksByWorld.remove(world);
      } else {
        pendingBlocksByWorld.put(world, remaining);
      }
    }
  }

  private boolean hasPendingAdmission(UUID world) {
    if (world == null) {
      return false;
    }
    synchronized (admissionLock) {
      return pendingBlocksByWorld.getOrDefault(world, 0) > 0;
    }
  }

  private void processExplosionBlock(ExplosionWork work, float yield) {
    ExplosionHost host = explosions;
    GameLocation location = work.location;
    if (host == null || location == null) {
      return;
    }

    String type = host.blockType(location);
    if (type == null || !type.equals(work.expectedType)) {
      return;
    }
    if (TNT.equals(work.expectedType)) {
      if (work.directTnt) {
        host.clear(location, fastBlockUpdates);
        if (work.chainPermit) {
          host.explode(location, 4f);
        }
      }
      return;
    }

    if (M.r((double) yield)) {
      host.dropContents(location);
    }

    host.clear(location, fastBlockUpdates);
  }

  private static void reportError(Throwable failure) {
    ReactComponents.reportError("Unexpected React failure", failure);
  }

  @Override
  public int getTickInterval() {
    return COUNTER_INTERVAL_MILLIS;
  }

  @Override
  public void onTick() {
    window.reset();
    retryPendingBatches(MAX_RETRY_BATCHES_PER_TICK);
  }

  private static long packChunk(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
  }

  private static final class ExplosionChunkBatch {
    private final long id;
    private final GameLocation anchor;
    private final UUID world;
    private final float yield;
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean executing = new AtomicBoolean();
    private final Deque<ExplosionWork> work;

    private ExplosionChunkBatch(long id, ExplosionWork first, float yield) {
      this(id, new ArrayList<>(List.of(first)), yield);
    }

    private ExplosionChunkBatch(long id, List<ExplosionWork> work, float yield) {
      this.id = id;
      this.yield = yield;
      this.work = new ArrayDeque<>(work);
      anchor = work.get(0).location;
      world = anchor.worldId();
    }

    private void retainFirst(int count) {
      while (work.size() > count) {
        work.removeLast();
      }
    }
  }

  private record ExplosionWork(
      GameLocation location,
      String expectedType,
      boolean directTnt,
      boolean chainPermit
  ) {
  }
}
