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

package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PdcWriteBatcher implements IController, Listener {
  public static final String ID = "pdc-write-batcher";
  private static final int FLUSH_INTERVAL_TICKS = 1;
  private static final int DEFAULT_BYPASS_RADIUS = 16;
  private static final int MAX_PENDING_PER_CHUNK = 256;
  private static final int MAX_WRITE_ATTEMPTS = 3;
  private static final long DEFAULT_STOP_DRAIN_TIMEOUT_MILLIS = 30_000L;
  private static final long STOP_DRAIN_RETRY_MILLIS = 50L;

  private transient final Map<UUID, WorldQueue> queuesByWorld = new ConcurrentHashMap<>();
  private transient final Map<UUID, WorldUnavailableDisposition> unavailableDispositions = new ConcurrentHashMap<>();
  private transient final Set<UUID> unavailableWorlds = ConcurrentHashMap.newKeySet();
  private transient final Object drainMonitor = new Object();
  private transient final AtomicLong deferredCount = new AtomicLong(0L);
  private transient final AtomicLong immediateCount = new AtomicLong(0L);
  private transient final AtomicLong flushedCount = new AtomicLong(0L);
  private transient final AtomicLong outstandingWriteCount = new AtomicLong(0L);
  private transient final AtomicLong terminalFailureCount = new AtomicLong(0L);
  private transient final AtomicLong retiredUnavailableWriteCount = new AtomicLong(0L);
  private transient volatile boolean acceptingWrites = true;
  private transient int repeatingTaskId;
  private transient int bypassRadius = DEFAULT_BYPASS_RADIUS;
  private transient long stopDrainTimeoutMillis = DEFAULT_STOP_DRAIN_TIMEOUT_MILLIS;

  @Override
  public String getName() {
    return "Pdc Write Batcher";
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public void start() {
    retryTerminalFailures();
    if (outstandingWriteCount.get() == 0L) {
      queuesByWorld.clear();
    }
    deferredCount.set(0L);
    immediateCount.set(0L);
    flushedCount.set(0L);
    retiredUnavailableWriteCount.set(0L);
    unavailableDispositions.clear();
    unavailableWorlds.clear();
    acceptingWrites = true;
    repeatingTaskId = J.sr(this::flushAll, FLUSH_INTERVAL_TICKS);
  }

  @Override
  public void stop() {
    acceptingWrites = false;
    if (repeatingTaskId != 0) {
      J.csr(repeatingTaskId);
      repeatingTaskId = 0;
    }

    try {
      boolean drained = awaitDrain(stopDrainTimeoutMillis);
      long retiredUnavailable = retiredUnavailableWriteCount.get();
      if (drained && retiredUnavailable == 0L) {
        queuesByWorld.clear();
        return;
      }
      String reason;
      if (terminalFailureCount.get() > 0L) {
        reason = "encountered " + terminalFailureCount.get()
            + " writes that failed after " + MAX_WRITE_ATTEMPTS + " attempts";
      } else if (retiredUnavailable > 0L) {
        reason = "retired " + retiredUnavailable
            + " writes with disposition=FAILED_WORLD_UNAVAILABLE";
      } else {
        reason = "timed out after " + stopDrainTimeoutMillis + "ms";
      }
      throw undrainedWritesFailure(reason, null);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw undrainedWritesFailure("was interrupted", exception);
    }
  }

  @Override
  public void postStart() {
  }

  public void setBypassRadius(int blocks) {
    bypassRadius = Math.max(0, blocks);
  }

  public int getBypassRadius() {
    return bypassRadius;
  }

  void setStopDrainTimeoutMillis(long timeoutMillis) {
    stopDrainTimeoutMillis = Math.max(0L, timeoutMillis);
  }

  public long getDeferredCount() {
    return deferredCount.get();
  }

  public long getImmediateCount() {
    return immediateCount.get();
  }

  public long getFlushedCount() {
    return flushedCount.get();
  }

  public long getRetiredUnavailableWriteCount() {
    return retiredUnavailableWriteCount.get();
  }

  public long readAndResetDeferred() {
    return deferredCount.getAndSet(0L);
  }

  public long readAndResetImmediate() {
    return immediateCount.getAndSet(0L);
  }

  public long readAndResetFlushed() {
    return flushedCount.getAndSet(0L);
  }

  public void enqueue(BlockState state, boolean force, boolean applyPhysics) {
    if (state == null) {
      return;
    }

    World world = state.getWorld();
    if (world == null) {
      applyNow(state, force, applyPhysics);
      return;
    }

    UUID worldId = world.getUID();
    long chunkKey = packChunk(state.getX() >> 4, state.getZ() >> 4);
    if (unavailableWorlds.contains(worldId)) {
      recordUnavailableWrite(worldId, chunkKey, "enqueue observed a world already unavailable");
      return;
    }

    Location location = new Location(world, state.getX(), state.getY(), state.getZ());
    if (bypassRadius > 0 && React.hasNearbyPlayer(location, bypassRadius)) {
      applyNow(state, force, applyPhysics);
      return;
    }

    WorldQueue worldQueue = queuesByWorld.computeIfAbsent(worldId, ignored -> new WorldQueue());
    boolean queued;
    boolean unavailable;
    Lock availability = worldQueue.availabilityLock.readLock();
    availability.lock();
    try {
      unavailable = !worldQueue.available || unavailableWorlds.contains(worldId);
      synchronized (worldQueue) {
        ChunkQueue chunkQueue = worldQueue.chunks.get(chunkKey);
        if (!acceptingWrites || unavailable) {
          queued = false;
        } else {
          if (chunkQueue == null) {
            chunkQueue = new ChunkQueue();
            worldQueue.chunks.put(chunkKey, chunkQueue);
          }

          if (chunkQueue.outstandingCount >= MAX_PENDING_PER_CHUNK) {
            queued = false;
          } else {
            chunkQueue.pending.add(state, force, applyPhysics);
            chunkQueue.outstandingCount++;
            outstandingWriteCount.incrementAndGet();
            queued = true;
          }
        }
      }
    } finally {
      availability.unlock();
    }

    if (unavailable) {
      recordUnavailableWrite(worldId, chunkKey, "enqueue raced world unavailability");
      return;
    }
    if (!queued) {
      applyNow(state, force, applyPhysics);
      return;
    }

    deferredCount.incrementAndGet();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkUnload(ChunkUnloadEvent event) {
    Chunk chunk = event.getChunk();
    World world = chunk.getWorld();
    drainChunkOnOwner(world.getUID(), packChunk(chunk.getX(), chunk.getZ()));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onWorldUnload(WorldUnloadEvent event) {
    if (event.isCancelled()) {
      return;
    }

    UUID worldId = event.getWorld().getUID();
    unavailableWorlds.add(worldId);
    WorldQueue worldQueue = queuesByWorld.get(worldId);
    if (worldQueue != null) {
      retireWorldQueue(worldId, worldQueue, "WorldUnloadEvent completed before queued writes drained");
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onWorldLoad(WorldLoadEvent event) {
    unavailableWorlds.remove(event.getWorld().getUID());
  }

  private void applyNow(BlockState state, boolean force, boolean applyPhysics) {
    immediateCount.incrementAndGet();
    safelyApply(state, force, applyPhysics);
  }

  void flushAll() {
    if (queuesByWorld.isEmpty()) {
      return;
    }

    for (Map.Entry<UUID, WorldQueue> worldEntry : queuesByWorld.entrySet()) {
      try {
        flushWorld(worldEntry.getKey(), worldEntry.getValue());
      } catch (Throwable throwable) {
        React.reportError(new IllegalStateException(
            "Failed to flush deferred PDC writes for world=" + worldEntry.getKey(), throwable));
      }
    }
  }

  private void flushWorld(UUID worldId, WorldQueue worldQueue) {
    World world = Bukkit.getWorld(worldId);
    if (world == null) {
      retireWorldQueue(worldId, worldQueue, "Bukkit no longer exposes the owning world");
      return;
    }

    Lock availability = worldQueue.availabilityLock.readLock();
    availability.lock();
    try {
      if (!worldQueue.available) {
        return;
      }

      boolean foliaThreading = J.isFoliaThreading();
      long[] chunkKeys;
      ChunkQueue[] chunkQueues;
      InFlight[] flights;
      synchronized (worldQueue) {
        int count = 0;
        ObjectIterator<Long2ObjectOpenHashMap.Entry<ChunkQueue>> countIterator =
            worldQueue.chunks.long2ObjectEntrySet().fastIterator();
        while (countIterator.hasNext()) {
          ChunkQueue chunkQueue = countIterator.next().getValue();
          if (chunkQueue.inFlight == null && chunkQueue.pending.size > 0) {
            count++;
          }
        }
        if (count == 0) {
          return;
        }

        chunkKeys = new long[count];
        chunkQueues = new ChunkQueue[count];
        flights = new InFlight[count];
        int index = 0;
        ObjectIterator<Long2ObjectOpenHashMap.Entry<ChunkQueue>> iterator =
            worldQueue.chunks.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
          Long2ObjectOpenHashMap.Entry<ChunkQueue> entry = iterator.next();
          ChunkQueue chunkQueue = entry.getValue();
          if (chunkQueue.inFlight != null || chunkQueue.pending.size == 0) {
            continue;
          }

          PendingChunk bucket = chunkQueue.pending;
          InFlight flight = new InFlight(bucket, bucket.size, System.nanoTime());
          chunkQueue.pending = new PendingChunk();
          chunkQueue.inFlight = flight;
          chunkKeys[index] = entry.getLongKey();
          chunkQueues[index] = chunkQueue;
          flights[index] = flight;
          index++;
        }
      }

      for (int index = 0; index < chunkKeys.length; index++) {
        long chunkKey = chunkKeys[index];
        ChunkQueue chunkQueue = chunkQueues[index];
        InFlight flight = flights[index];
        int chunkX = (int) (chunkKey >> 32);
        int chunkZ = (int) chunkKey;
        if (foliaThreading) {
          try {
            if (!J.runChunk(world, chunkX, chunkZ,
                () -> executeFlight(worldId, chunkKey, worldQueue, chunkQueue, flight))) {
              reinsertFlight(worldId, chunkKey, worldQueue, chunkQueue, flight);
            }
          } catch (Throwable throwable) {
            reinsertFlight(worldId, chunkKey, worldQueue, chunkQueue, flight);
            React.reportError(new IllegalStateException(
                flightContext("Failed to schedule deferred PDC writes", worldId, chunkKey, flight.count),
                throwable));
          }
          continue;
        }

        executeFlight(worldId, chunkKey, worldQueue, chunkQueue, flight);
      }
    } finally {
      availability.unlock();
    }
  }

  private boolean executeFlight(UUID worldId, long chunkKey, WorldQueue worldQueue, ChunkQueue chunkQueue,
                                InFlight flight) {
    Lock availability = worldQueue.availabilityLock.readLock();
    availability.lock();
    try {
      if (!worldQueue.available || !flight.executionClaimed.compareAndSet(false, true)) {
        return false;
      }

      ApplyResult result;
      try {
        result = applyBucket(flight.bucket);
      } catch (Throwable throwable) {
        React.reportError(new IllegalStateException(
            flightContext("Failed to apply deferred PDC writes", worldId, chunkKey, flight.count), throwable));
        reinsertFlight(worldId, chunkKey, worldQueue, chunkQueue, flight);
        return true;
      }
      acknowledgeFlight(worldId, chunkKey, worldQueue, chunkQueue, flight, result);
      return true;
    } finally {
      availability.unlock();
    }
  }

  private ApplyResult applyBucket(PendingChunk bucket) {
    if (bucket == null) {
      return new ApplyResult(0, new PendingChunk(), new PendingChunk());
    }

    int size;
    BlockState[] states;
    boolean[] forceFlags;
    boolean[] applyPhysicsFlags;
    byte[] attempts;
    synchronized (bucket) {
      size = bucket.size;
      if (size == 0) {
        return new ApplyResult(0, new PendingChunk(), new PendingChunk());
      }
      states = new BlockState[size];
      forceFlags = new boolean[size];
      applyPhysicsFlags = new boolean[size];
      attempts = new byte[size];
      System.arraycopy(bucket.states, 0, states, 0, size);
      System.arraycopy(bucket.force, 0, forceFlags, 0, size);
      System.arraycopy(bucket.applyPhysics, 0, applyPhysicsFlags, 0, size);
      System.arraycopy(bucket.attempts, 0, attempts, 0, size);
      bucket.clear();
    }

    int applied = 0;
    PendingChunk retry = new PendingChunk();
    PendingChunk failed = new PendingChunk();
    for (int i = 0; i < size; i++) {
      if (safelyApply(states[i], forceFlags[i], applyPhysicsFlags[i])) {
        applied++;
      } else if (attempts[i] < MAX_WRITE_ATTEMPTS) {
        retry.add(states[i], forceFlags[i], applyPhysicsFlags[i], (byte) (attempts[i] + 1));
      } else {
        failed.add(states[i], forceFlags[i], applyPhysicsFlags[i], attempts[i]);
      }
    }
    flushedCount.addAndGet(applied);
    return new ApplyResult(applied, retry, failed);
  }

  private void acknowledgeFlight(UUID worldId, long chunkKey, WorldQueue worldQueue, ChunkQueue chunkQueue,
                                 InFlight flight, ApplyResult result) {
    String failure = null;
    synchronized (worldQueue) {
      ChunkQueue current = worldQueue.chunks.get(chunkKey);
      if (current != chunkQueue || chunkQueue.inFlight != flight) {
        failure = flightContext("Could not acknowledge deferred PDC writes", worldId, chunkKey, flight.count);
      } else {
        chunkQueue.inFlight = null;
        chunkQueue.pending.append(result.retry);
        chunkQueue.failed.append(result.failed);
        chunkQueue.outstandingCount -= result.applied;
        outstandingWriteCount.addAndGet(-result.applied);
        terminalFailureCount.addAndGet(result.failed.size);
        if (chunkQueue.outstandingCount == 0) {
          worldQueue.chunks.remove(chunkKey);
        }
      }
    }
    signalDrain();
    if (failure != null) {
      React.reportError(new IllegalStateException(failure));
    }
  }

  private void reinsertFlight(UUID worldId, long chunkKey, WorldQueue worldQueue, ChunkQueue chunkQueue,
                              InFlight flight) {
    String failure = null;
    synchronized (worldQueue) {
      ChunkQueue current = worldQueue.chunks.get(chunkKey);
      if (current != chunkQueue || chunkQueue.inFlight != flight) {
        failure = flightContext("Could not reinsert rejected deferred PDC writes", worldId, chunkKey, flight.count);
      } else {
        int combinedCount = flight.count + chunkQueue.pending.size;
        if (combinedCount != chunkQueue.outstandingCount || combinedCount > MAX_PENDING_PER_CHUNK) {
          failure = flightContext(
              "Rejected deferred PDC writes violated the per-chunk cap; retained in-flight ownership",
              worldId,
              chunkKey,
              flight.count);
        } else {
          flight.bucket.append(chunkQueue.pending);
          chunkQueue.pending = flight.bucket;
          chunkQueue.inFlight = null;
        }
      }
    }
    signalDrain();
    if (failure != null) {
      React.reportError(new IllegalStateException(failure));
    }
  }

  private void drainChunkOnOwner(UUID worldId, long chunkKey) {
    WorldQueue worldQueue = queuesByWorld.get(worldId);
    if (worldQueue == null) {
      return;
    }

    int failed = 0;
    Lock availability = worldQueue.availabilityLock.readLock();
    availability.lock();
    try {
      while (worldQueue.available) {
        ChunkQueue chunkQueue;
        InFlight flight;
        synchronized (worldQueue) {
          chunkQueue = worldQueue.chunks.get(chunkKey);
          if (chunkQueue == null) {
            break;
          }
          if (chunkQueue.inFlight == null && chunkQueue.pending.size > 0) {
            PendingChunk bucket = chunkQueue.pending;
            chunkQueue.pending = new PendingChunk();
            chunkQueue.inFlight = new InFlight(bucket, bucket.size, System.nanoTime());
          }
          flight = chunkQueue.inFlight;
          if (flight == null) {
            failed = chunkQueue.failed.size;
            if (failed > 0 && !chunkQueue.terminalFailureReported) {
              chunkQueue.terminalFailureReported = true;
            } else {
              failed = 0;
            }
            break;
          }
        }

        if (!executeFlight(worldId, chunkKey, worldQueue, chunkQueue, flight)) {
          break;
        }
      }
    } finally {
      availability.unlock();
    }

    if (failed > 0) {
      React.reportError(new IllegalStateException(flightContext(
          "Deferred PDC writes exhausted retries before chunk unload",
          worldId,
          chunkKey,
          failed)));
    }
  }

  private void retireWorldQueue(UUID worldId, WorldQueue worldQueue, String reason) {
    unavailableWorlds.add(worldId);
    long retired = 0L;
    long terminalFailures = 0L;
    StringBuilder chunks = new StringBuilder();
    Lock availability = worldQueue.availabilityLock.writeLock();
    availability.lock();
    try {
      if (!worldQueue.available) {
        return;
      }
      worldQueue.available = false;
      synchronized (worldQueue) {
        ObjectIterator<Long2ObjectOpenHashMap.Entry<ChunkQueue>> iterator =
            worldQueue.chunks.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
          Long2ObjectOpenHashMap.Entry<ChunkQueue> entry = iterator.next();
          long chunkKey = entry.getLongKey();
          ChunkQueue chunkQueue = entry.getValue();
          if (chunkQueue.outstandingCount == 0) {
            continue;
          }
          if (!chunks.isEmpty()) {
            chunks.append("; ");
          }
          chunks.append("chunkX=").append((int) (chunkKey >> 32))
              .append(",chunkZ=").append((int) chunkKey)
              .append(",pending=").append(chunkQueue.pending.size)
              .append(",inFlight=").append(chunkQueue.inFlight == null ? 0 : chunkQueue.inFlight.count)
              .append(",failed=").append(chunkQueue.failed.size);
          retired += chunkQueue.outstandingCount;
          terminalFailures += chunkQueue.failed.size;
          chunkQueue.pending.clear();
          chunkQueue.failed.clear();
          if (chunkQueue.inFlight != null) {
            chunkQueue.inFlight.bucket.clear();
          }
          chunkQueue.inFlight = null;
          chunkQueue.outstandingCount = 0;
        }
        worldQueue.chunks.clear();
      }
      queuesByWorld.remove(worldId, worldQueue);
      if (retired > 0L) {
        outstandingWriteCount.addAndGet(-retired);
        terminalFailureCount.addAndGet(-terminalFailures);
        retiredUnavailableWriteCount.addAndGet(retired);
        recordUnavailableDisposition(worldId, retired, reason, chunks.toString());
      }
    } finally {
      availability.unlock();
    }

    signalDrain();
    if (retired > 0L) {
      React.reportError(new IllegalStateException(
          "Deferred PDC writes reached disposition=FAILED_WORLD_UNAVAILABLE"
              + "; world=" + worldId
              + "; writes=" + retired
              + "; reason=" + reason
              + "; chunks=[" + chunks + "]"));
    }
  }

  private void recordUnavailableWrite(UUID worldId, long chunkKey, String reason) {
    retiredUnavailableWriteCount.incrementAndGet();
    String detail = "chunkX=" + (int) (chunkKey >> 32) + ",chunkZ=" + (int) chunkKey + ",writes=1";
    recordUnavailableDisposition(worldId, 1L, reason, detail);
    signalDrain();
    React.reportError(new IllegalStateException(
        "Deferred PDC write reached disposition=FAILED_WORLD_UNAVAILABLE"
            + "; world=" + worldId
            + "; " + detail
            + "; reason=" + reason));
  }

  private void recordUnavailableDisposition(UUID worldId, long writes, String reason, String details) {
    unavailableDispositions.compute(worldId, (ignored, current) -> {
      if (current == null) {
        return new WorldUnavailableDisposition(writes, reason, details);
      }
      return new WorldUnavailableDisposition(
          current.writes() + writes,
          current.reason() + " | " + reason,
          current.details() + "; " + details);
    });
  }

  private boolean awaitDrain(long timeoutMillis) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
    while (outstandingWriteCount.get() > 0L) {
      if (terminalFailureCount.get() > 0L) {
        return false;
      }
      flushAll();
      if (outstandingWriteCount.get() == 0L) {
        return true;
      }
      if (terminalFailureCount.get() > 0L) {
        return false;
      }

      long remainingNanos = deadline - System.nanoTime();
      if (remainingNanos <= 0L) {
        return false;
      }

      long waitMillis = Math.max(1L, Math.min(
          STOP_DRAIN_RETRY_MILLIS,
          TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
      synchronized (drainMonitor) {
        if (outstandingWriteCount.get() == 0L) {
          return true;
        }
        drainMonitor.wait(waitMillis);
      }
    }
    return true;
  }

  private void signalDrain() {
    synchronized (drainMonitor) {
      drainMonitor.notifyAll();
    }
  }

  private void retryTerminalFailures() {
    long retried = 0L;
    for (WorldQueue worldQueue : queuesByWorld.values()) {
      synchronized (worldQueue) {
        ObjectIterator<Long2ObjectOpenHashMap.Entry<ChunkQueue>> iterator =
            worldQueue.chunks.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
          ChunkQueue chunkQueue = iterator.next().getValue();
          if (chunkQueue.failed.size == 0) {
            continue;
          }
          chunkQueue.failed.resetAttempts();
          retried += chunkQueue.failed.size;
          chunkQueue.pending.append(chunkQueue.failed);
          chunkQueue.failed = new PendingChunk();
          chunkQueue.terminalFailureReported = false;
        }
      }
    }
    if (retried > 0L) {
      terminalFailureCount.addAndGet(-retried);
    }
  }

  private IllegalStateException undrainedWritesFailure(String reason, Throwable cause) {
    StringBuilder details = new StringBuilder();
    int chunkCount = 0;
    long observedWrites = 0L;
    long now = System.nanoTime();
    for (Map.Entry<UUID, WorldQueue> worldEntry : queuesByWorld.entrySet()) {
      WorldQueue worldQueue = worldEntry.getValue();
      synchronized (worldQueue) {
        ObjectIterator<Long2ObjectOpenHashMap.Entry<ChunkQueue>> iterator =
            worldQueue.chunks.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
          Long2ObjectOpenHashMap.Entry<ChunkQueue> entry = iterator.next();
          ChunkQueue chunkQueue = entry.getValue();
          if (chunkQueue.outstandingCount == 0) {
            continue;
          }

          long chunkKey = entry.getLongKey();
          InFlight flight = chunkQueue.inFlight;
          long inFlightAgeMillis = flight == null
              ? 0L
              : TimeUnit.NANOSECONDS.toMillis(Math.max(0L, now - flight.claimedAtNanos));
          if (!details.isEmpty()) {
            details.append("; ");
          }
          details.append("world=").append(worldEntry.getKey())
              .append(",chunkX=").append((int) (chunkKey >> 32))
              .append(",chunkZ=").append((int) chunkKey)
              .append(",pending=").append(chunkQueue.pending.size)
              .append(",inFlight=").append(flight == null ? 0 : flight.count)
              .append(",failed=").append(chunkQueue.failed.size)
              .append(",inFlightAgeMs=").append(inFlightAgeMillis);
          chunkCount++;
          observedWrites += chunkQueue.outstandingCount;
        }
      }
    }

    String message = "PDC write batcher stop " + reason
        + "; outstandingWrites=" + outstandingWriteCount.get()
        + ", terminalFailures=" + terminalFailureCount.get()
        + ", retiredUnavailableWrites=" + retiredUnavailableWriteCount.get()
        + ", observedWrites=" + observedWrites
        + ", chunks=" + chunkCount
        + ", details=[" + details + "]"
        + ", unavailableDispositions=[" + unavailableDispositionDetails() + "]";
    return cause == null
        ? new IllegalStateException(message)
        : new IllegalStateException(message, cause);
  }

  private String unavailableDispositionDetails() {
    StringBuilder details = new StringBuilder();
    for (Map.Entry<UUID, WorldUnavailableDisposition> entry : unavailableDispositions.entrySet()) {
      if (!details.isEmpty()) {
        details.append("; ");
      }
      WorldUnavailableDisposition disposition = entry.getValue();
      details.append("world=").append(entry.getKey())
          .append(",writes=").append(disposition.writes())
          .append(",reason=").append(disposition.reason())
          .append(",chunks=[").append(disposition.details()).append(']');
    }
    return details.toString();
  }

  private static String flightContext(String action, UUID worldId, long chunkKey, int count) {
    return action
        + "; world=" + worldId
        + ", chunkX=" + (int) (chunkKey >> 32)
        + ", chunkZ=" + (int) chunkKey
        + ", writes=" + count;
  }

  private boolean safelyApply(BlockState state, boolean force, boolean applyPhysics) {
    if (state == null) {
      return false;
    }
    try {
      return state.update(force, applyPhysics);
    } catch (Throwable throwable) {
      String context;
      try {
        World world = state.getWorld();
        context = "world=" + (world == null ? "null" : world.getUID())
            + ",x=" + state.getX()
            + ",y=" + state.getY()
            + ",z=" + state.getZ();
      } catch (Throwable contextFailure) {
        throwable.addSuppressed(contextFailure);
        context = "stateType=" + state.getClass().getName();
      }
      React.reportError(new IllegalStateException(
          "Failed to apply PDC write; " + context
              + ",force=" + force
              + ",applyPhysics=" + applyPhysics,
          throwable));
      return false;
    }
  }

  private static long packChunk(int cx, int cz) {
    return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
  }

  private static final class WorldQueue {
    private final Long2ObjectOpenHashMap<ChunkQueue> chunks = new Long2ObjectOpenHashMap<>();
    private final ReentrantReadWriteLock availabilityLock = new ReentrantReadWriteLock();
    private boolean available = true;
  }

  private static final class ChunkQueue {
    private PendingChunk pending = new PendingChunk();
    private PendingChunk failed = new PendingChunk();
    private InFlight inFlight;
    private int outstandingCount;
    private boolean terminalFailureReported;
  }

  private static final class InFlight {
    private final PendingChunk bucket;
    private final int count;
    private final long claimedAtNanos;
    private final AtomicBoolean executionClaimed = new AtomicBoolean(false);

    private InFlight(PendingChunk bucket, int count, long claimedAtNanos) {
      this.bucket = bucket;
      this.count = count;
      this.claimedAtNanos = claimedAtNanos;
    }
  }

  private record ApplyResult(int applied, PendingChunk retry, PendingChunk failed) {
  }

  private record WorldUnavailableDisposition(long writes, String reason, String details) {
  }

  private static final class PendingChunk {
    private BlockState[] states;
    private boolean[] force;
    private boolean[] applyPhysics;
    private byte[] attempts;
    private int size;

    private PendingChunk() {
      states = new BlockState[8];
      force = new boolean[8];
      applyPhysics = new boolean[8];
      attempts = new byte[8];
      size = 0;
    }

    private void add(BlockState state, boolean forceFlag, boolean applyPhysicsFlag) {
      add(state, forceFlag, applyPhysicsFlag, (byte) 1);
    }

    private void add(BlockState state, boolean forceFlag, boolean applyPhysicsFlag, byte attempt) {
      ensureCapacity(size + 1);
      states[size] = state;
      force[size] = forceFlag;
      applyPhysics[size] = applyPhysicsFlag;
      attempts[size] = attempt;
      size++;
    }

    private void append(PendingChunk other) {
      if (other.size == 0) {
        return;
      }
      ensureCapacity(size + other.size);
      System.arraycopy(other.states, 0, states, size, other.size);
      System.arraycopy(other.force, 0, force, size, other.size);
      System.arraycopy(other.applyPhysics, 0, applyPhysics, size, other.size);
      System.arraycopy(other.attempts, 0, attempts, size, other.size);
      size += other.size;
    }

    private void ensureCapacity(int required) {
      if (states.length >= required) {
        return;
      }
      int newCapacity = states.length;
      while (newCapacity < required) {
        newCapacity *= 2;
      }
      BlockState[] grownStates = new BlockState[newCapacity];
      boolean[] grownForce = new boolean[newCapacity];
      boolean[] grownApplyPhysics = new boolean[newCapacity];
      byte[] grownAttempts = new byte[newCapacity];
      System.arraycopy(states, 0, grownStates, 0, size);
      System.arraycopy(force, 0, grownForce, 0, size);
      System.arraycopy(applyPhysics, 0, grownApplyPhysics, 0, size);
      System.arraycopy(attempts, 0, grownAttempts, 0, size);
      states = grownStates;
      force = grownForce;
      applyPhysics = grownApplyPhysics;
      attempts = grownAttempts;
    }

    private void resetAttempts() {
      for (int i = 0; i < size; i++) {
        attempts[i] = 1;
      }
    }

    private void clear() {
      for (int i = 0; i < size; i++) {
        states[i] = null;
      }
      size = 0;
    }
  }
}
