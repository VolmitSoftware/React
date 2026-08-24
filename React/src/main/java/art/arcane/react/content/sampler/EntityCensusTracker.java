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

package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class EntityCensusTracker {
  private static final Object LOCK = new Object();
  private static final long REFRESH_INTERVAL_MS = 2000L;
  private static final int MAX_PAPER_ENTITIES_PER_WORLD_REFRESH = 128;
  private static final int MAX_FOLIA_CHUNKS_PER_REFRESH = 32;
  private static final int MAX_FOLIA_ENTITIES_PER_CHUNK = 128;
  private static final int GROUND_ITEM = 1;
  private static final int HOSTILE = 1 << 1;
  private static final int ANIMAL = 1 << 2;
  private static final int VILLAGER = 1 << 3;
  private static final int PROJECTILE = 1 << 4;
  private static final int PHYSICS = 1 << 5;
  private static final int ACTIVE_AI = 1 << 6;
  private static final AtomicLong lastRefreshMS = new AtomicLong(0L);
  private static final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private static final AtomicBoolean foliaRefreshInFlight = new AtomicBoolean(false);
  private static final Map<UUID, Integer> observedCategories = new ConcurrentHashMap<>();
  private static final Map<UUID, Map<Long, ChunkScanCursor>> chunkScanCursors = new ConcurrentHashMap<>();
  private static final AtomicInteger groundItems = new AtomicInteger(0);
  private static final AtomicInteger hostile = new AtomicInteger(0);
  private static final AtomicInteger animals = new AtomicInteger(0);
  private static final AtomicInteger villagers = new AtomicInteger(0);
  private static final AtomicInteger projectiles = new AtomicInteger(0);
  private static final AtomicInteger physics = new AtomicInteger(0);
  private static final AtomicInteger activeAi = new AtomicInteger(0);
  private static volatile boolean accepting;
  private static int references;

  private EntityCensusTracker() {
  }

  static void acquire() {
    synchronized (LOCK) {
      references++;
      accepting = true;
    }
  }

  static void release() {
    synchronized (LOCK) {
      references = Math.max(0, references - 1);
      if (references > 0) {
        return;
      }

      accepting = false;
      lifecycleGeneration.incrementAndGet();
      lastRefreshMS.set(0L);
      foliaRefreshInFlight.set(false);
      chunkScanCursors.clear();
      observedCategories.clear();
      store(0, 0, 0, 0, 0, 0, 0);
    }
  }

  static int groundItems() {
    return Math.max(0, groundItems.get());
  }

  static int hostile() {
    return Math.max(0, hostile.get());
  }

  static int animals() {
    return Math.max(0, animals.get());
  }

  static int villagers() {
    return Math.max(0, villagers.get());
  }

  static int projectiles() {
    return Math.max(0, projectiles.get());
  }

  static int physics() {
    return Math.max(0, physics.get());
  }

  static int activeAi() {
    return Math.max(0, activeAi.get());
  }

  static void observe(Entity entity) {
    if (!accepting || entity == null) {
      return;
    }

    UUID entityId = entity.getUniqueId();
    int categories = classify(entity);
    observedCategories.compute(entityId, (ignored, existing) -> {
      int previous = existing == null ? 0 : existing;
      if (previous == categories) {
        return existing;
      }

      adjust(previous, -1);
      adjust(categories, 1);
      return categories == 0 ? null : categories;
    });
  }

  static void forget(Entity entity) {
    if (entity != null) {
      forget(entity.getUniqueId());
    }
  }

  static void forget(UUID entityId) {
    if (!accepting || entityId == null) {
      return;
    }

    observedCategories.computeIfPresent(entityId, (ignored, categories) -> {
      adjust(categories, -1);
      return null;
    });
  }

  static void chunkUnloaded(UUID worldId, int chunkX, int chunkZ) {
    if (worldId == null) {
      return;
    }

    Map<Long, ChunkScanCursor> worldCursors = chunkScanCursors.get(worldId);
    if (worldCursors == null) {
      return;
    }
    worldCursors.remove(packChunk(chunkX, chunkZ));
    if (worldCursors.isEmpty()) {
      chunkScanCursors.remove(worldId, worldCursors);
    }
  }

  static void worldUnloaded(UUID worldId) {
    if (worldId != null) {
      chunkScanCursors.remove(worldId);
    }
  }

  static void refreshMainThread() {
    if (!claimWindow()) {
      return;
    }

    for (World world : Bukkit.getWorlds()) {
      for (Entity entity : WorldEntitySnapshots.next(world, MAX_PAPER_ENTITIES_PER_WORLD_REFRESH)) {
        SamplerEntities.reconcileCurrentChunk(entity, entity.getChunk());
        observe(entity);
      }
    }
  }

  static void refreshFolia() {
    long generation;
    synchronized (LOCK) {
      if (!foliaRefreshInFlight.compareAndSet(false, true)) {
        return;
      }
      if (!claimWindow()) {
        foliaRefreshInFlight.set(false);
        return;
      }
      generation = lifecycleGeneration.get();
    }

    boolean scheduled;
    try {
      scheduled = FoliaScheduler.runGlobal(React.instance, () -> dispatchFoliaChunks(generation));
    } catch (Throwable failure) {
      React.reportError(failure);
      releaseFoliaRefresh(generation);
      return;
    }
    if (!scheduled) {
      releaseFoliaRefresh(generation);
    }
  }

  static long coverageHorizonMS(int loadedChunkCount) {
    if (loadedChunkCount <= 0) {
      return 0L;
    }

    long batches = (loadedChunkCount + (long) MAX_FOLIA_CHUNKS_PER_REFRESH - 1L)
        / MAX_FOLIA_CHUNKS_PER_REFRESH;
    return batches * REFRESH_INTERVAL_MS;
  }

  static long coverageHorizonMS(int loadedChunkCount, int maximumEntitiesPerChunk) {
    if (loadedChunkCount <= 0 || maximumEntitiesPerChunk <= 0) {
      return 0L;
    }

    long entityPasses = (maximumEntitiesPerChunk + (long) MAX_FOLIA_ENTITIES_PER_CHUNK - 1L)
        / MAX_FOLIA_ENTITIES_PER_CHUNK;
    long chunkPassMS = coverageHorizonMS(loadedChunkCount);
    if (entityPasses > Long.MAX_VALUE / chunkPassMS) {
      return Long.MAX_VALUE;
    }
    return entityPasses * chunkPassMS;
  }

  private static void dispatchFoliaChunks(long generation) {
    try {
      if (!isCurrent(generation)) {
        return;
      }

      ObserverController observer = React.controller(ObserverController.class);
      List<ObserverController.LoadedChunkTarget> targets = observer == null
          ? List.of()
          : observer.nextLoadedChunkCoordinateBatch(MAX_FOLIA_CHUNKS_PER_REFRESH);
      if (targets.isEmpty()) {
        releaseFoliaRefresh(generation);
        return;
      }

      AtomicInteger remaining = new AtomicInteger(targets.size());
      for (ObserverController.LoadedChunkTarget target : targets) {
        AtomicBoolean completed = new AtomicBoolean(false);
        Runnable completion = () -> {
          if (completed.compareAndSet(false, true) && remaining.decrementAndGet() == 0) {
            releaseFoliaRefresh(generation);
          }
        };
        Runnable scan = () -> {
          try {
            if (isCurrent(generation)) {
              scanFoliaChunk(target, generation);
            }
          } catch (Throwable failure) {
            React.reportError(failure);
          } finally {
            completion.run();
          }
        };

        boolean scheduled;
        try {
          World world = Bukkit.getWorld(target.worldId());
          scheduled = world != null && J.runChunk(world, target.chunkX(), target.chunkZ(), scan);
        } catch (Throwable failure) {
          React.reportError(failure);
          scheduled = false;
        }
        if (!scheduled) {
          completion.run();
        }
      }
    } catch (Throwable failure) {
      React.reportError(failure);
      releaseFoliaRefresh(generation);
    }
  }

  private static void scanFoliaChunk(ObserverController.LoadedChunkTarget target, long generation) {
    World world = Bukkit.getWorld(target.worldId());
    if (!isCurrent(generation)
        || world == null
        || !world.isChunkLoaded(target.chunkX(), target.chunkZ())) {
      chunkUnloaded(target.worldId(), target.chunkX(), target.chunkZ());
      return;
    }

    Chunk chunk = world.getChunkAt(target.chunkX(), target.chunkZ(), false);
    if (!chunk.isEntitiesLoaded()) {
      return;
    }
    Entity[] entities = chunk.getEntities();
    if (entities.length == 0) {
      chunkUnloaded(target.worldId(), target.chunkX(), target.chunkZ());
      return;
    }

    Map<Long, ChunkScanCursor> worldCursors = chunkScanCursors.computeIfAbsent(
        target.worldId(),
        ignored -> new ConcurrentHashMap<>()
    );
    ChunkScanCursor cursor = worldCursors.computeIfAbsent(
        packChunk(target.chunkX(), target.chunkZ()),
        ignored -> new ChunkScanCursor()
    );
    int count = Math.min(MAX_FOLIA_ENTITIES_PER_CHUNK, entities.length);
    int start = cursor.claim(entities.length, count);
    for (int offset = 0; offset < count; offset++) {
      Entity entity = entities[(start + offset) % entities.length];
      if (entity != null && J.isOwnedByCurrentRegion(entity)) {
        SamplerEntities.reconcileCurrentChunk(entity, chunk);
        observe(entity);
      }
    }
  }

  private static long packChunk(int chunkX, int chunkZ) {
    return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
  }

  private static void releaseFoliaRefresh(long generation) {
    synchronized (LOCK) {
      if (generation == lifecycleGeneration.get()) {
        foliaRefreshInFlight.set(false);
      }
    }
  }

  private static boolean claimWindow() {
    long now = System.currentTimeMillis();
    long last = lastRefreshMS.get();
    if (now - last < REFRESH_INTERVAL_MS) {
      return false;
    }

    return lastRefreshMS.compareAndSet(last, now);
  }

  private static boolean isCurrent(long generation) {
    return accepting && generation == lifecycleGeneration.get();
  }

  private static int classify(Entity entity) {
    int categories = 0;
    if (entity instanceof Item) {
      categories |= GROUND_ITEM;
    }
    if (entity instanceof Enemy) {
      categories |= HOSTILE;
    }
    if (entity instanceof Animals) {
      categories |= ANIMAL;
    }
    if (entity instanceof AbstractVillager) {
      categories |= VILLAGER;
    }
    if (entity instanceof Projectile) {
      categories |= PROJECTILE;
    }
    if (entity instanceof TNTPrimed || entity instanceof FallingBlock) {
      categories |= PHYSICS;
    }
    if (entity instanceof LivingEntity living
        && !(entity instanceof Player)
        && !living.isDead()
        && living.hasAI()) {
      categories |= ACTIVE_AI;
    }
    return categories;
  }

  private static void adjust(int categories, int delta) {
    if ((categories & GROUND_ITEM) != 0) {
      groundItems.addAndGet(delta);
    }
    if ((categories & HOSTILE) != 0) {
      hostile.addAndGet(delta);
    }
    if ((categories & ANIMAL) != 0) {
      animals.addAndGet(delta);
    }
    if ((categories & VILLAGER) != 0) {
      villagers.addAndGet(delta);
    }
    if ((categories & PROJECTILE) != 0) {
      projectiles.addAndGet(delta);
    }
    if ((categories & PHYSICS) != 0) {
      physics.addAndGet(delta);
    }
    if ((categories & ACTIVE_AI) != 0) {
      activeAi.addAndGet(delta);
    }
  }

  private static void store(int ground, int enemy, int passive, int trader, int flying, int falling, int active) {
    groundItems.set(ground);
    hostile.set(enemy);
    animals.set(passive);
    villagers.set(trader);
    projectiles.set(flying);
    physics.set(falling);
    activeAi.set(active);
  }

  static final class ChunkScanCursor {
    private int next;

    synchronized int claim(int entityCount, int limit) {
      if (entityCount <= 0 || limit <= 0) {
        next = 0;
        return 0;
      }

      int start = Math.floorMod(next, entityCount);
      next = (int) (((long) start + Math.min(entityCount, limit)) % entityCount);
      return start;
    }
  }
}
