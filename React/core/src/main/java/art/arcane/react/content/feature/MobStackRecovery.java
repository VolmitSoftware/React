package art.arcane.react.content.feature;

import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.MobStackHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class MobStackRecovery {
  static final int MAX_SPAWNS_PER_BATCH = 16;
  static final int MAX_ENTITIES_PER_BATCH = 256;
  private static final long SWEEP_INTERVAL_MS = 5_000L;
  private static final long WARNING_INTERVAL_MS = 60_000L;

  private final FeatureMobStacking feature;
  private final AtomicBoolean pending = new AtomicBoolean();
  private final AtomicBoolean rearmRequested = new AtomicBoolean();
  private MobStackHost.ChunkCursor cursor;
  private ChunkRef target;
  private List<GameEntity> entities;
  private int offset;
  private long generation = -1L;
  private long nextSweepAt;
  private long nextWarningAt;
  private volatile boolean dormant;
  private boolean sweepFoundStack;

  MobStackRecovery(FeatureMobStacking feature) {
    this.feature = feature;
  }

  void tick(long currentGeneration) {
    if (!pending.compareAndSet(false, true)) {
      return;
    }
    boolean scheduled = false;
    try {
      if (!feature.shouldRecoverStacks(currentGeneration)) {
        reset();
        return;
      }
      if (generation != currentGeneration) {
        reset();
        generation = currentGeneration;
      }
      MobStackHost host = feature.stacks();
      if (host == null || (target == null && !nextTarget(host))) {
        return;
      }
      ReactWorld world = world(target);
      if (world == null) {
        clearTarget();
        return;
      }
      scheduled = J.runChunk(world.handle(), target.chunkX(), target.chunkZ(), () -> {
        try {
          restoreChunk(host, world, currentGeneration);
        } catch (RuntimeException failure) {
          sweepFoundStack = true;
          clearTarget();
          ReactPlatforms.get().warn("Could not restore a mob stack; its remaining count will be retried.", failure);
        } finally {
          pending.set(false);
        }
      }, 1);
    } finally {
      if (!scheduled) {
        pending.set(false);
      }
    }
  }

  void rearm() {
    rearmRequested.set(true);
  }

  boolean needsRearm() {
    return dormant && !rearmRequested.get();
  }

  void warnRejectedSpawn(GameEntity source) {
    sweepFoundStack = true;
    long now = System.currentTimeMillis();
    if (now >= nextWarningAt) {
      nextWarningAt = now + WARNING_INTERVAL_MS;
      ReactPlatforms.get().warn("Could not restore " + source.type() + " stack " + source.id()
          + "; a replacement spawn was rejected. Its remaining count will be retried.");
    }
  }

  private static ReactWorld world(ChunkRef target) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    WorldHost worlds = platform == null ? null : platform.worldHost().orElse(null);
    return worlds == null ? null : worlds.world(target.worldId()).orElse(null);
  }

  private boolean nextTarget(MobStackHost host) {
    if (cursor == null) {
      if (rearmRequested.getAndSet(false)) {
        dormant = false;
        nextSweepAt = 0L;
      }
      if (dormant || System.currentTimeMillis() < nextSweepAt) {
        return false;
      }
      MobStackHost.ChunkCursor opened = host.openLoadedChunks();
      if (opened == null) {
        return false;
      }
      cursor = opened;
      sweepFoundStack = false;
    }
    List<ChunkRef> targets = cursor.next(1);
    if (targets.isEmpty()) {
      cursor = null;
      if (sweepFoundStack) {
        nextSweepAt = System.currentTimeMillis() + SWEEP_INTERVAL_MS;
      } else {
        dormant = true;
      }
      return false;
    }
    target = targets.get(0);
    return true;
  }

  private void restoreChunk(MobStackHost host, ReactWorld world, long currentGeneration) {
    if (!feature.shouldRecoverStacks(currentGeneration)
        || !host.isChunkLoaded(world, target.chunkX(), target.chunkZ())) {
      clearTarget();
      return;
    }
    if (entities == null) {
      entities = host.loadedChunkEntities(world, target.chunkX(), target.chunkZ());
      if (entities == null) {
        clearTarget();
        return;
      }
    }
    int remainingSpawns = MAX_SPAWNS_PER_BATCH;
    int inspected = 0;
    boolean folia = J.isFoliaThreading();
    while (offset < entities.size() && inspected++ < MAX_ENTITIES_PER_BATCH && remainingSpawns > 0
        && feature.shouldRecoverStacks(currentGeneration)) {
      GameEntity entity = entities.get(offset);
      if (host.isLiving(entity) && folia && !J.isOwnedByCurrentRegion(entity.handle())) {
        sweepFoundStack = true;
      } else if (host.isLiving(entity) && host.isAlive(entity)) {
        int restored = feature.restoreStack(entity, remainingSpawns, currentGeneration);
        remainingSpawns -= restored;
        boolean stacked = feature.getStackCount(entity) > 1;
        if (restored > 0 || stacked) {
          sweepFoundStack = true;
        }
        if (remainingSpawns == 0 && stacked) {
          return;
        }
      }
      offset++;
    }
    if (offset >= entities.size()) {
      clearTarget();
    }
  }

  private void reset() {
    cursor = null;
    nextSweepAt = 0L;
    dormant = false;
    sweepFoundStack = false;
    clearTarget();
  }

  private void clearTarget() {
    target = null;
    entities = null;
    offset = 0;
  }
}
