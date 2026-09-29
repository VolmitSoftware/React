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
import art.arcane.react.React;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.volmlib.util.format.Form;
import com.google.common.util.concurrent.AtomicDouble;
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
  private transient Listener paperMoveListener;
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
    registerPaperMoveListener();
    refreshEntityCount();
  }

  @Override
  public void stop() {
    acceptingEntityEvents = false;
    unregisterPaperMoveListener();
    if (activeInstance == this) {
      activeInstance = null;
    }
    clearTrackedBuckets();
    WorldEntitySnapshots.invalidate();
    super.stop();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntitySpawnEvent e) {
    track(e.getEntity(), e.getLocation());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntitiesLoadEvent e) {
    Chunk chunk = e.getChunk();
    World world = chunk.getWorld();
    int chunkX = chunk.getX();
    int chunkZ = chunk.getZ();
    for (Entity entity : e.getEntities()) {
      track(entity, world, chunkX, chunkZ);
    }
    WorldEntitySnapshots.markChunkReconciled(chunk);
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
    track(e.getPlayer(), e.getPlayer().getLocation());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    untrack(e.getPlayer());
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

  void move(Entity entity, Location destination) {
    if (destination == null) {
      return;
    }
    World destinationWorld = destination.getWorld();
    if (destinationWorld == null) {
      return;
    }
    WorldEntitySnapshots.observe(entity, destinationWorld);
    if (!acceptingEntityEvents) {
      EntityCensusTracker.observe(entity);
      return;
    }

    UUID entityId = entity.getUniqueId();
    UUID worldId = destinationWorld.getUID();
    int chunkX = destination.getBlockX() >> 4;
    int chunkZ = destination.getBlockZ() >> 4;
    TrackedEntity existing = trackedEntities.get(entityId);
    if (existing != null && existing.coordinate().matches(worldId, chunkX, chunkZ)) {
      return;
    }

    relocate(entityId, destinationWorld, chunkX, chunkZ, false);
    EntityCensusTracker.observe(entity);
  }

  private void registerPaperMoveListener() {
    if (paperMoveListener != null) {
      return;
    }

    String probeFailure = null;
    try {
      Class.forName("io.papermc.paper.event.entity.EntityMoveEvent");
    } catch (Throwable ex) {
      probeFailure = ex.getClass().getSimpleName();
    }

    if (probeFailure != null || React.instance == null) {
      React.verbose("Entity move events unavailable ("
          + (probeFailure == null ? "no plugin instance" : probeFailure)
          + "); entity chunk crossings follow the census reconcile pass.");
      return;
    }

    paperMoveListener = new SamplerEntitiesPaperMoveListener(this);
    React.instance.registerListener(paperMoveListener);
  }

  private void unregisterPaperMoveListener() {
    Listener listener = paperMoveListener;
    paperMoveListener = null;
    if (listener != null && React.instance != null) {
      React.instance.unregisterListener(listener);
    }
  }

  private void refreshEntityCount() {
    J.sync(() -> entities.set(countWorldEntities(Bukkit.getWorlds())));
  }

  private void track(Entity entity, Location location) {
    World world = location.getWorld();
    track(entity, world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
  }

  private void track(Entity entity, World world, int chunkX, int chunkZ) {
    WorldEntitySnapshots.observe(entity, world);
    EntityCensusTracker.observe(entity);
    if (!acceptingEntityEvents || world == null) {
      return;
    }

    relocate(entity.getUniqueId(), world, chunkX, chunkZ, true);
  }

  private void reconcile(Entity entity, Chunk chunk) {
    if (!acceptingEntityEvents || entity == null || chunk == null) {
      return;
    }

    World world = chunk.getWorld();
    WorldEntitySnapshots.observe(entity, world);
    if (world != null) {
      relocate(entity.getUniqueId(), world, chunk.getX(), chunk.getZ(), false);
    }
  }

  private void relocate(UUID entityId, World world, int chunkX, int chunkZ, boolean countNewEntity) {
    UUID worldId = world.getUID();
    TrackedEntity existing = trackedEntities.get(entityId);
    if (existing != null && existing.coordinate().matches(worldId, chunkX, chunkZ)) {
      return;
    }

    ChunkCoordinate coordinate = new ChunkCoordinate(worldId, chunkX, chunkZ);
    AtomicDouble counter = React.controller(ObserverController.class).get(world, chunkX, chunkZ, this);
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
    private boolean matches(UUID otherWorldId, int otherChunkX, int otherChunkZ) {
      return chunkX == otherChunkX && chunkZ == otherChunkZ && worldId.equals(otherWorldId);
    }
  }

  private record TrackedEntity(ChunkCoordinate coordinate, AtomicDouble counter) {
  }
}
