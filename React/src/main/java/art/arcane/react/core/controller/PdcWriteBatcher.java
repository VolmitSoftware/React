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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PdcWriteBatcher implements IController {
  public static final String ID = "pdc-write-batcher";
  private static final int FLUSH_INTERVAL_TICKS = 1;
  private static final int DEFAULT_BYPASS_RADIUS = 16;
  private static final int MAX_PENDING_PER_CHUNK = 256;

  private transient final Map<UUID, Long2ObjectOpenHashMap<PendingChunk>> pendingByWorld = new ConcurrentHashMap<>();
  private transient final AtomicLong deferredCount = new AtomicLong(0L);
  private transient final AtomicLong immediateCount = new AtomicLong(0L);
  private transient final AtomicLong flushedCount = new AtomicLong(0L);
  private transient int repeatingTaskId;
  private transient int bypassRadius = DEFAULT_BYPASS_RADIUS;

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
    pendingByWorld.clear();
    deferredCount.set(0L);
    immediateCount.set(0L);
    flushedCount.set(0L);
    repeatingTaskId = J.sr(this::flushAll, FLUSH_INTERVAL_TICKS);
  }

  @Override
  public void stop() {
    if (repeatingTaskId != 0) {
      J.csr(repeatingTaskId);
      repeatingTaskId = 0;
    }
    flushAll();
    pendingByWorld.clear();
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

  public long getDeferredCount() {
    return deferredCount.get();
  }

  public long getImmediateCount() {
    return immediateCount.get();
  }

  public long getFlushedCount() {
    return flushedCount.get();
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

    Location location = new Location(world, state.getX(), state.getY(), state.getZ());
    if (bypassRadius > 0 && React.hasNearbyPlayer(location, bypassRadius)) {
      applyNow(state, force, applyPhysics);
      return;
    }

    long chunkKey = packChunk(state.getX() >> 4, state.getZ() >> 4);
    Long2ObjectOpenHashMap<PendingChunk> chunkMap = pendingByWorld
        .computeIfAbsent(world.getUID(), ignored -> new Long2ObjectOpenHashMap<>());
    boolean queued;
    synchronized (chunkMap) {
      PendingChunk bucket = chunkMap.get(chunkKey);
      if (bucket == null) {
        bucket = new PendingChunk();
        chunkMap.put(chunkKey, bucket);
      }

      if (bucket.size >= MAX_PENDING_PER_CHUNK) {
        queued = false;
      } else {
        bucket.add(state, force, applyPhysics);
        queued = true;
      }
    }

    if (!queued) {
      applyNow(state, force, applyPhysics);
      return;
    }

    deferredCount.incrementAndGet();
  }

  private void applyNow(BlockState state, boolean force, boolean applyPhysics) {
    immediateCount.incrementAndGet();
    safelyApply(state, force, applyPhysics);
  }

  private void flushAll() {
    if (pendingByWorld.isEmpty()) {
      return;
    }

    Iterator<Map.Entry<UUID, Long2ObjectOpenHashMap<PendingChunk>>> worldIterator = pendingByWorld.entrySet().iterator();
    while (worldIterator.hasNext()) {
      Map.Entry<UUID, Long2ObjectOpenHashMap<PendingChunk>> worldEntry = worldIterator.next();
      Long2ObjectOpenHashMap<PendingChunk> chunkMap = worldEntry.getValue();
      flushWorld(worldEntry.getKey(), chunkMap);
      synchronized (chunkMap) {
        if (chunkMap.isEmpty()) {
          worldIterator.remove();
        }
      }
    }
  }

  private void flushWorld(UUID worldId, Long2ObjectOpenHashMap<PendingChunk> chunkMap) {
    World world = Bukkit.getWorld(worldId);
    if (world == null) {
      synchronized (chunkMap) {
        chunkMap.clear();
      }
      return;
    }

    boolean foliaThreading = J.isFoliaThreading();
    long[] chunkKeys;
    synchronized (chunkMap) {
      int count = chunkMap.size();
      if (count == 0) {
        return;
      }
      chunkKeys = new long[count];
      int index = 0;
      ObjectIterator<Long2ObjectOpenHashMap.Entry<PendingChunk>> iterator = chunkMap.long2ObjectEntrySet().fastIterator();
      while (iterator.hasNext()) {
        chunkKeys[index++] = iterator.next().getLongKey();
      }
    }

    for (long chunkKey : chunkKeys) {
      PendingChunk bucket;
      synchronized (chunkMap) {
        bucket = chunkMap.remove(chunkKey);
      }
      if (bucket == null) {
        continue;
      }

      int chunkX = (int) (chunkKey >> 32);
      int chunkZ = (int) chunkKey;
      if (foliaThreading) {
        PendingChunk bucketRef = bucket;
        if (!J.runChunk(world, chunkX, chunkZ, () -> applyBucket(bucketRef))) {
          reinsertBucket(chunkMap, chunkKey, bucket);
        }
        continue;
      }

      applyBucket(bucket);
    }
  }

  private void applyBucket(PendingChunk bucket) {
    if (bucket == null) {
      return;
    }

    int size;
    BlockState[] states;
    boolean[] forceFlags;
    boolean[] applyPhysicsFlags;
    synchronized (bucket) {
      size = bucket.size;
      if (size == 0) {
        return;
      }
      states = new BlockState[size];
      forceFlags = new boolean[size];
      applyPhysicsFlags = new boolean[size];
      System.arraycopy(bucket.states, 0, states, 0, size);
      System.arraycopy(bucket.force, 0, forceFlags, 0, size);
      System.arraycopy(bucket.applyPhysics, 0, applyPhysicsFlags, 0, size);
      bucket.clear();
    }

    long applied = 0L;
    for (int i = 0; i < size; i++) {
      if (safelyApply(states[i], forceFlags[i], applyPhysicsFlags[i])) {
        applied++;
      }
    }
    flushedCount.addAndGet(applied);
  }

  private void reinsertBucket(Long2ObjectOpenHashMap<PendingChunk> chunkMap, long chunkKey, PendingChunk bucket) {
    synchronized (chunkMap) {
      PendingChunk existing = chunkMap.get(chunkKey);
      if (existing == null) {
        chunkMap.put(chunkKey, bucket);
        return;
      }
      synchronized (bucket) {
        synchronized (existing) {
          for (int i = 0; i < bucket.size; i++) {
            existing.add(bucket.states[i], bucket.force[i], bucket.applyPhysics[i]);
          }
        }
      }
    }
  }

  private boolean safelyApply(BlockState state, boolean force, boolean applyPhysics) {
    if (state == null) {
      return false;
    }
    try {
      return state.update(force, applyPhysics);
    } catch (Throwable ex) {
      React.reportError(ex);
      return false;
    }
  }

  private static long packChunk(int cx, int cz) {
    return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
  }

  private static final class PendingChunk {
    private BlockState[] states;
    private boolean[] force;
    private boolean[] applyPhysics;
    private int size;

    private PendingChunk() {
      states = new BlockState[8];
      force = new boolean[8];
      applyPhysics = new boolean[8];
      size = 0;
    }

    private void add(BlockState state, boolean forceFlag, boolean applyPhysicsFlag) {
      ensureCapacity(size + 1);
      states[size] = state;
      force[size] = forceFlag;
      applyPhysics[size] = applyPhysicsFlag;
      size++;
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
      System.arraycopy(states, 0, grownStates, 0, size);
      System.arraycopy(force, 0, grownForce, 0, size);
      System.arraycopy(applyPhysics, 0, grownApplyPhysics, 0, size);
      states = grownStates;
      force = grownForce;
      applyPhysics = grownApplyPhysics;
    }

    private void clear() {
      for (int i = 0; i < size; i++) {
        states[i] = null;
      }
      size = 0;
    }
  }
}
