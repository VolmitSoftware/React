package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.ObserverController.LoadedChunkCursor;
import art.arcane.react.core.controller.ObserverController.LoadedChunkTarget;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

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
  private LoadedChunkCursor cursor;
  private LoadedChunkTarget target;
  private Entity[] entities;
  private int offset;
  private long generation = -1L;
  private long nextSweepAt;
  private long nextWarningAt;
  private boolean dormant;
  private boolean sweepRestored;

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
      if (target == null && !nextTarget()) {
        return;
      }
      World world = Bukkit.getWorld(target.worldId());
      if (world == null) {
        clearTarget();
        return;
      }
      scheduled = J.runChunk(world, target.chunkX(), target.chunkZ(), () -> {
        try {
          restoreChunk(world, currentGeneration);
        } catch (RuntimeException failure) {
          clearTarget();
          React.warn("Could not restore a mob stack; its remaining count will be retried.", failure);
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

  void warnRejectedSpawn(LivingEntity source) {
    sweepRestored = true;
    long now = System.currentTimeMillis();
    if (now >= nextWarningAt) {
      nextWarningAt = now + WARNING_INTERVAL_MS;
      React.warn("Could not restore " + source.getType() + " stack " + source.getUniqueId()
          + "; a replacement spawn was rejected. Its remaining count will be retried.");
    }
  }

  private boolean nextTarget() {
    if (cursor == null) {
      if (rearmRequested.getAndSet(false)) {
        dormant = false;
        nextSweepAt = 0L;
      }
      if (dormant || System.currentTimeMillis() < nextSweepAt) {
        return false;
      }
      ObserverController observer = React.controller(ObserverController.class);
      if (observer == null || !observer.isLoadedChunkCoordinateIndexReady()) {
        return false;
      }
      cursor = observer.openLoadedChunkCursor();
      sweepRestored = false;
    }
    List<LoadedChunkTarget> targets = cursor.next(1);
    if (targets.isEmpty()) {
      cursor = null;
      if (sweepRestored) {
        nextSweepAt = System.currentTimeMillis() + SWEEP_INTERVAL_MS;
      } else {
        dormant = true;
      }
      return false;
    }
    target = targets.getFirst();
    return true;
  }

  private void restoreChunk(World world, long currentGeneration) {
    if (!feature.shouldRecoverStacks(currentGeneration)
        || !world.isChunkLoaded(target.chunkX(), target.chunkZ())) {
      clearTarget();
      return;
    }
    Chunk chunk = world.getChunkAt(target.chunkX(), target.chunkZ(), false);
    if (chunk == null || !chunk.isEntitiesLoaded()) {
      clearTarget();
      return;
    }
    if (entities == null) {
      entities = chunk.getEntities();
    }
    int remainingSpawns = MAX_SPAWNS_PER_BATCH;
    int inspected = 0;
    while (offset < entities.length && inspected++ < MAX_ENTITIES_PER_BATCH && remainingSpawns > 0
        && feature.shouldRecoverStacks(currentGeneration)) {
      Entity entity = entities[offset];
      if (entity instanceof LivingEntity living
          && (!J.isFoliaThreading() || J.isOwnedByCurrentRegion(entity))
          && entity.isValid() && !entity.isDead()) {
        int restored = feature.restoreStack(living, remainingSpawns, currentGeneration);
        if (restored > 0) {
          sweepRestored = true;
        }
        remainingSpawns -= restored;
        if (remainingSpawns == 0 && feature.getStackCount(entity) > 1) {
          return;
        }
      }
      offset++;
    }
    if (offset >= entities.length) {
      clearTarget();
    }
  }

  private void reset() {
    cursor = null;
    nextSweepAt = 0L;
    dormant = false;
    sweepRestored = false;
    clearTarget();
  }

  private void clearTarget() {
    target = null;
    entities = null;
    offset = 0;
  }
}
