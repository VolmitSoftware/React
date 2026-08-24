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

import art.arcane.chrono.ChronoLatch;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.volmlib.util.format.Form;
import com.google.common.util.concurrent.AtomicDouble;
import io.papermc.paper.event.entity.EntityMoveEvent;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SamplerEntities extends ReactCachedSampler implements Listener {
  public static final String ID = "entities";
  private static volatile SamplerEntities activeInstance;
  @Getter
  private transient final AtomicInteger entities;
  private transient final Map<UUID, TrackedEntity> trackedEntities;
  private transient ChronoLatch realEntityUpdate;
  private transient volatile boolean acceptingEntityEvents;
  private int realityCheckMS = 10000;

  public SamplerEntities() {
    super(ID, 50);
    entities = new AtomicInteger(0);
    trackedEntities = new ConcurrentHashMap<>();
    realEntityUpdate = new ChronoLatch(realityCheckMS);
  }

  @Override
  public Material getIcon() {
    return Material.CHICKEN_SPAWN_EGG;
  }

  public int getRealCheck() {
    return countWorldEntities(Bukkit.getWorlds());
  }

  @Override
  public void start() {
    super.start();
    acceptingEntityEvents = false;
    clearTrackedBuckets();
    WorldEntitySnapshots.invalidate();
    entities.set(0);
    realEntityUpdate = new ChronoLatch(realityCheckMS);
    activeInstance = this;
    acceptingEntityEvents = true;
    refreshEntityCount();
  }

  @Override
  public void stop() {
    acceptingEntityEvents = false;
    if (activeInstance == this) {
      activeInstance = null;
    }
    clearTrackedBuckets();
    WorldEntitySnapshots.invalidate();
    super.stop();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntitySpawnEvent e) {
    track(e.getEntity(), e.getLocation().getChunk());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntitiesLoadEvent e) {
    for (Entity entity : e.getEntities()) {
      track(entity, e.getChunk());
    }
    WorldEntitySnapshots.markChunkReconciled(e.getChunk());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntitiesUnloadEvent e) {
    for (Entity entity : e.getEntities()) {
      untrack(entity);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ChunkUnloadEvent e) {
    Chunk chunk = e.getChunk();
    WorldEntitySnapshots.chunkUnloaded(
        chunk.getWorld().getUID(),
        chunk.getX(),
        chunk.getZ()
    );
    EntityCensusTracker.chunkUnloaded(
        chunk.getWorld().getUID(),
        chunk.getX(),
        chunk.getZ()
    );
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(WorldUnloadEvent e) {
    WorldEntitySnapshots.worldUnloaded(e.getWorld().getUID());
    EntityCensusTracker.worldUnloaded(e.getWorld().getUID());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityRemoveEvent e) {
    if (e.getCause() == EntityRemoveEvent.Cause.UNLOAD
        || e.getCause() == EntityRemoveEvent.Cause.PLAYER_QUIT) {
      return;
    }
    untrack(e.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    track(e.getPlayer(), e.getPlayer().getLocation().getChunk());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    untrack(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityMoveEvent e) {
    move(e.getEntity(), e.getTo());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    move(e.getPlayer(), e.getTo());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityTeleportEvent e) {
    move(e.getEntity(), e.getTo());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(VehicleMoveEvent e) {
    move(e.getVehicle(), e.getTo());
  }

  @Override
  public double onSample() {
    if (realEntityUpdate.flip() || entities.get() < 0) {
      refreshEntityCount();
    }

    return entities.get();
  }

  static int countWorldEntities(List<World> worlds) {
    long count = 0L;
    for (World world : worlds) {
      count += Math.max(0, WorldEntitySnapshots.count(world));
      if (count >= Integer.MAX_VALUE) {
        return Integer.MAX_VALUE;
      }
    }

    return (int) count;
  }

  static void reconcileCurrentChunk(Entity entity, Chunk chunk) {
    SamplerEntities active = activeInstance;
    if (active != null) {
      active.reconcile(entity, chunk);
    }
  }

  private void refreshEntityCount() {
    J.sync(() -> entities.set(countWorldEntities(Bukkit.getWorlds())));
  }

  private void track(Entity entity, Chunk chunk) {
    WorldEntitySnapshots.observe(entity, chunk.getWorld());
    EntityCensusTracker.observe(entity);
    if (!acceptingEntityEvents) {
      return;
    }

    relocate(entity.getUniqueId(), chunk, true);
  }

  private void reconcile(Entity entity, Chunk chunk) {
    if (!acceptingEntityEvents || entity == null || chunk == null) {
      return;
    }

    WorldEntitySnapshots.observe(entity, chunk.getWorld());
    relocate(entity.getUniqueId(), chunk, false);
  }

  private void relocate(UUID entityId, Chunk chunk, boolean countNewEntity) {
    ChunkCoordinate coordinate = ChunkCoordinate.of(chunk);
    TrackedEntity existing = trackedEntities.get(entityId);
    if (existing != null && existing.coordinate().equals(coordinate)) {
      return;
    }

    AtomicDouble counter = getChunkCounter(chunk);
    trackedEntities.compute(entityId, (ignored, current) -> {
      if (current == null) {
        if (countNewEntity) {
          entities.incrementAndGet();
        }
        counter.addAndGet(1D);
        return new TrackedEntity(coordinate, counter);
      }
      if (current.coordinate().equals(coordinate)) {
        return current;
      }

      decrement(current.counter());
      counter.addAndGet(1D);
      return new TrackedEntity(coordinate, counter);
    });
  }

  private void untrack(Entity entity) {
    UUID entityId = entity.getUniqueId();
    WorldEntitySnapshots.forget(entityId);
    EntityCensusTracker.forget(entityId);
    if (!acceptingEntityEvents) {
      return;
    }

    TrackedEntity tracked = trackedEntities.remove(entityId);
    entities.updateAndGet(current -> Math.max(0, current - 1));
    if (tracked != null) {
      decrement(tracked.counter());
    }
  }

  private void move(Entity entity, Location destination) {
    if (destination == null) {
      return;
    }
    World destinationWorld = destination.getWorld();
    if (destinationWorld != null) {
      WorldEntitySnapshots.observe(entity, destinationWorld);
    }
    if (!acceptingEntityEvents) {
      EntityCensusTracker.observe(entity);
      return;
    }

    UUID entityId = entity.getUniqueId();
    TrackedEntity existing = trackedEntities.get(entityId);
    Chunk chunk = destination.getChunk();
    ChunkCoordinate coordinate = ChunkCoordinate.of(chunk);
    if (existing != null && existing.coordinate().equals(coordinate)) {
      return;
    }

    relocate(entityId, chunk, false);
    EntityCensusTracker.observe(entity);
  }

  private void clearTrackedBuckets() {
    for (Map.Entry<UUID, TrackedEntity> entry : trackedEntities.entrySet()) {
      if (trackedEntities.remove(entry.getKey(), entry.getValue())) {
        decrement(entry.getValue().counter());
      }
    }
  }

  private void decrement(AtomicDouble counter) {
    counter.updateAndGet(current -> Math.max(0D, current - 1D));
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return "ENT";
  }

  private record ChunkCoordinate(UUID worldId, int chunkX, int chunkZ) {
    private static ChunkCoordinate of(Chunk chunk) {
      return new ChunkCoordinate(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
  }

  private record TrackedEntity(ChunkCoordinate coordinate, AtomicDouble counter) {
  }
}
