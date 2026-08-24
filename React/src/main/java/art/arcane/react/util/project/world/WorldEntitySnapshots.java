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

package art.arcane.react.util.project.world;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WorldEntitySnapshots {
  public static final int DEFAULT_SAMPLE_LIMIT = 256;
  private static final Map<UUID, EntityReference> ENTITIES_BY_ID = new ConcurrentHashMap<>();
  private static final Map<UUID, WorldIndex> ENTITIES_BY_WORLD = new ConcurrentHashMap<>();
  private static final Map<ChunkCoordinate, ReconcileCursor> RECONCILE_CURSORS = new ConcurrentHashMap<>();
  private static final Map<UUID, Map<ChunkCoordinate, Boolean>> RECONCILED_CHUNKS = new ConcurrentHashMap<>();
  private static final ConcurrentLinkedQueue<ChunkCoordinate> PARTIAL_CHUNKS = new ConcurrentLinkedQueue<>();
  private static volatile boolean paperEntityCountAvailable = true;
  private static volatile boolean paperChunkCountAvailable = true;

  private WorldEntitySnapshots() {
  }

  public static void observe(Entity entity) {
    if (entity == null || entity.getWorld() == null) {
      return;
    }
    observe(entity, entity.getWorld());
  }

  public static void observe(Entity entity, World world) {
    if (entity == null || world == null) {
      return;
    }

    UUID entityId = entity.getUniqueId();
    UUID worldId = world.getUID();
    EntityReference replacement = new EntityReference(entityId, worldId, entity);
    ENTITIES_BY_ID.compute(entityId, (ignored, existing) -> {
      if (existing != null && existing.worldId.equals(worldId) && existing.get() == entity) {
        return existing;
      }
      if (existing != null) {
        removeFromWorld(existing);
      }
      ENTITIES_BY_WORLD.computeIfAbsent(worldId, ignoredWorld -> new WorldIndex()).add(replacement);
      return replacement;
    });
  }

  public static void forget(Entity entity) {
    if (entity != null) {
      forget(entity.getUniqueId());
    }
  }

  public static void forget(UUID entityId) {
    if (entityId == null) {
      return;
    }

    ENTITIES_BY_ID.computeIfPresent(entityId, (ignored, existing) -> {
      removeFromWorld(existing);
      return null;
    });
  }

  public static List<Entity> next(World world, int maximum) {
    if (world == null || maximum <= 0) {
      return List.of();
    }

    WorldIndex index = ENTITIES_BY_WORLD.get(world.getUID());
    return index == null ? List.of() : index.next(maximum);
  }

  public static List<Entity> reconcileNextLoadedChunks(int maximumChunks, int maximumEntities) {
    if (maximumChunks <= 0 || maximumEntities <= 0 || J.isFoliaThreading()) {
      return List.of();
    }

    List<ChunkCoordinate> targets = claimReconcileTargets(maximumChunks);
    if (targets.isEmpty()) {
      return List.of();
    }

    List<Entity> reconciled = new ArrayList<>(Math.min(maximumEntities, DEFAULT_SAMPLE_LIMIT));
    int remainingEntities = maximumEntities;
    int processedChunks = 0;
    for (ChunkCoordinate target : targets) {
      if (processedChunks >= maximumChunks || remainingEntities <= 0) {
        break;
      }
      processedChunks++;
      remainingEntities -= reconcileChunk(target, remainingEntities, reconciled);
    }
    return reconciled;
  }

  public static void markChunkReconciled(Chunk chunk) {
    if (chunk == null || chunk.getWorld() == null) {
      return;
    }
    markChunkReconciled(new ChunkCoordinate(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
  }

  public static void chunkUnloaded(UUID worldId, int chunkX, int chunkZ) {
    if (worldId == null) {
      return;
    }
    ChunkCoordinate coordinate = new ChunkCoordinate(worldId, chunkX, chunkZ);
    RECONCILE_CURSORS.remove(coordinate);
    Map<ChunkCoordinate, Boolean> reconciled = RECONCILED_CHUNKS.get(worldId);
    if (reconciled != null) {
      reconciled.remove(coordinate);
      if (reconciled.isEmpty()) {
        RECONCILED_CHUNKS.remove(worldId, reconciled);
      }
    }
  }

  public static void worldUnloaded(UUID worldId) {
    if (worldId == null) {
      return;
    }

    WorldIndex removed = ENTITIES_BY_WORLD.remove(worldId);
    if (removed != null) {
      removed.removeAllFrom(ENTITIES_BY_ID);
    }
    RECONCILED_CHUNKS.remove(worldId);
    RECONCILE_CURSORS.keySet().removeIf(coordinate -> coordinate.worldId.equals(worldId));
  }

  public static int count(World world) {
    if (world == null) {
      return 0;
    }

    if (paperEntityCountAvailable) {
      try {
        return world.getEntityCount();
      } catch (Throwable ignored) {
        paperEntityCountAvailable = false;
      }
    }

    WorldIndex index = ENTITIES_BY_WORLD.get(world.getUID());
    return index == null ? 0 : index.size();
  }

  public static int chunkCount(World world) {
    if (world == null) {
      return 0;
    }

    if (paperChunkCountAvailable) {
      try {
        return world.getChunkCount();
      } catch (Throwable ignored) {
        paperChunkCountAvailable = false;
      }
    }

    Map<ChunkCoordinate, Boolean> reconciled = RECONCILED_CHUNKS.get(world.getUID());
    return reconciled == null ? 0 : reconciled.size();
  }

  public static void invalidate() {
    ENTITIES_BY_ID.clear();
    ENTITIES_BY_WORLD.clear();
    RECONCILE_CURSORS.clear();
    RECONCILED_CHUNKS.clear();
    PARTIAL_CHUNKS.clear();
    paperEntityCountAvailable = true;
    paperChunkCountAvailable = true;
  }

  private static List<ChunkCoordinate> claimReconcileTargets(int maximumChunks) {
    List<ChunkCoordinate> targets = new ArrayList<>(maximumChunks);
    while (targets.size() < maximumChunks) {
      ChunkCoordinate partial = PARTIAL_CHUNKS.poll();
      if (partial == null) {
        break;
      }
      ReconcileCursor cursor = RECONCILE_CURSORS.get(partial);
      if (cursor != null) {
        cursor.queued.set(false);
        targets.add(partial);
      }
    }
    if (targets.size() >= maximumChunks) {
      return targets;
    }

    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null) {
      return targets;
    }
    List<ObserverController.LoadedChunkTarget> indexed = observer.nextLoadedChunkCoordinateBatch(
        maximumChunks - targets.size()
    );
    for (ObserverController.LoadedChunkTarget target : indexed) {
      ChunkCoordinate coordinate = new ChunkCoordinate(target.worldId(), target.chunkX(), target.chunkZ());
      if (!isChunkReconciled(coordinate)) {
        targets.add(coordinate);
      }
      if (targets.size() >= maximumChunks) {
        break;
      }
    }
    return targets;
  }

  private static int reconcileChunk(
      ChunkCoordinate coordinate,
      int maximumEntities,
      List<Entity> reconciled
  ) {
    if (isChunkReconciled(coordinate)) {
      return 0;
    }

    World world = Bukkit.getWorld(coordinate.worldId);
    if (world == null || !world.isChunkLoaded(coordinate.chunkX, coordinate.chunkZ)) {
      chunkUnloaded(coordinate.worldId, coordinate.chunkX, coordinate.chunkZ);
      return 0;
    }

    Chunk chunk = world.getChunkAt(coordinate.chunkX, coordinate.chunkZ, false);
    if (chunk == null || !chunk.isEntitiesLoaded()) {
      return 0;
    }

    Entity[] entities = chunk.getEntities();
    if (entities.length == 0) {
      markChunkReconciled(coordinate);
      return 0;
    }

    ReconcileCursor cursor = RECONCILE_CURSORS.computeIfAbsent(
        coordinate,
        ignored -> new ReconcileCursor()
    );
    ReconcileWindow window = cursor.claim(entities.length, maximumEntities);
    for (int index = window.start; index < window.end; index++) {
      Entity entity = entities[index];
      if (entity == null) {
        continue;
      }
      observe(entity);
      reconciled.add(entity);
    }

    if (window.complete) {
      markChunkReconciled(coordinate);
    } else if (cursor.queued.compareAndSet(false, true)) {
      PARTIAL_CHUNKS.offer(coordinate);
    }
    return window.end - window.start;
  }

  private static boolean isChunkReconciled(ChunkCoordinate coordinate) {
    Map<ChunkCoordinate, Boolean> reconciled = RECONCILED_CHUNKS.get(coordinate.worldId);
    return reconciled != null && reconciled.containsKey(coordinate);
  }

  private static void markChunkReconciled(ChunkCoordinate coordinate) {
    RECONCILE_CURSORS.remove(coordinate);
    RECONCILED_CHUNKS.computeIfAbsent(
        coordinate.worldId,
        ignored -> new ConcurrentHashMap<>()
    ).put(coordinate, Boolean.TRUE);
  }

  private static void removeFromWorld(EntityReference reference) {
    WorldIndex index = ENTITIES_BY_WORLD.get(reference.worldId);
    if (index == null) {
      return;
    }
    index.remove(reference);
  }

  private static final class WorldIndex {
    private final Map<UUID, EntityReference> entities = new HashMap<>();
    private EntityReference first;
    private EntityReference last;

    private synchronized void add(EntityReference reference) {
      EntityReference existing = entities.put(reference.entityId, reference);
      if (existing != null) {
        unlink(existing);
      }
      linkLast(reference);
    }

    private synchronized void remove(EntityReference reference) {
      if (entities.get(reference.entityId) != reference) {
        return;
      }
      entities.remove(reference.entityId);
      unlink(reference);
    }

    private List<Entity> next(int maximum) {
      List<EntityReference> stale = new ArrayList<>();
      List<Entity> result;
      synchronized (this) {
        int attempts = entities.size();
        result = new ArrayList<>(Math.min(maximum, attempts));
        for (int attempt = 0; attempt < attempts && result.size() < maximum; attempt++) {
          EntityReference reference = first;
          if (reference == null) {
            break;
          }
          unlink(reference);
          Entity entity = reference.get();
          if (entity == null) {
            entities.remove(reference.entityId);
            stale.add(reference);
            continue;
          }

          result.add(entity);
          linkLast(reference);
        }
      }
      for (EntityReference reference : stale) {
        ENTITIES_BY_ID.remove(reference.entityId, reference);
      }
      return result;
    }

    private synchronized int size() {
      return entities.size();
    }

    private void removeAllFrom(Map<UUID, EntityReference> global) {
      List<EntityReference> removed;
      synchronized (this) {
        removed = new ArrayList<>(entities.values());
        entities.clear();
        first = null;
        last = null;
      }
      for (EntityReference reference : removed) {
        global.remove(reference.entityId, reference);
      }
    }

    private void linkLast(EntityReference reference) {
      reference.previous = last;
      reference.next = null;
      if (last == null) {
        first = reference;
      } else {
        last.next = reference;
      }
      last = reference;
    }

    private void unlink(EntityReference reference) {
      EntityReference previous = reference.previous;
      EntityReference next = reference.next;
      if (previous == null) {
        first = next;
      } else {
        previous.next = next;
      }
      if (next == null) {
        last = previous;
      } else {
        next.previous = previous;
      }
      reference.previous = null;
      reference.next = null;
    }
  }

  private static final class EntityReference extends WeakReference<Entity> {
    private final UUID entityId;
    private final UUID worldId;
    private EntityReference previous;
    private EntityReference next;

    private EntityReference(UUID entityId, UUID worldId, Entity entity) {
      super(entity);
      this.entityId = entityId;
      this.worldId = worldId;
    }
  }

  private static final class ReconcileCursor {
    private final AtomicBoolean queued = new AtomicBoolean(false);
    private int nextIndex;

    private synchronized ReconcileWindow claim(int entityCount, int maximum) {
      if (nextIndex >= entityCount) {
        nextIndex = 0;
      }
      int start = nextIndex;
      int end = start + Math.min(maximum, entityCount - start);
      nextIndex = end;
      return new ReconcileWindow(start, end, end >= entityCount);
    }
  }

  private record ChunkCoordinate(UUID worldId, int chunkX, int chunkZ) {
  }

  private record ReconcileWindow(int start, int end, boolean complete) {
  }
}
