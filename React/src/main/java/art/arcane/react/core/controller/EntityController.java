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

import art.arcane.chrono.ChronoLatch;
import art.arcane.react.React;
import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.api.protect.internal.ProtectionGuards;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.value.MaterialValue;
import art.arcane.react.util.project.world.EntityKiller;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.Looper;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityPoseChangeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Data
public class EntityController implements IController, Listener {
  private static final long MANAGED_CLEANUP_TIMEOUT_MS = 30_000L;
  private static final long FOLIA_PLAYER_SNAPSHOT_INTERVAL_MS = 1000L;
  private static final int INITIAL_RECONCILE_CHUNKS_PER_TICK = 1;
  private static final int INITIAL_RECONCILE_ENTITIES_PER_TICK = 256;

  private int perWorldUpdatesPerTick = 15;
  private transient final AtomicBoolean entityScanQueued = new AtomicBoolean(false);
  private transient final AtomicBoolean foliaPlayerSnapshotQueued = new AtomicBoolean(false);
  private transient final AtomicInteger nextFoliaPlayer = new AtomicInteger(0);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final AtomicReference<FoliaScanFlight> foliaScanFlight = new AtomicReference<>();
  private transient volatile Player[] foliaPlayers = new Player[0];
  private transient volatile long foliaPlayerSnapshotAtMS;
  private transient Looper looper;
  private transient ChronoLatch valueSaver = new ChronoLatch(60000);
  private transient ChronoLatch scratchSweeper = new ChronoLatch(30000);
  private transient Map<EntityType, List<Consumer<Entity>>> entityTickListeners;
  private transient List<Consumer<Entity>> allEntityTickListeners;

  public void registerEntityTickListener(EntityType type, Consumer<Entity> listener) {
    if (listener == null) {
      return;
    }

    entityTickListeners.computeIfAbsent(type, (t) -> new CopyOnWriteArrayList<>()).add(listener);
  }

  public void registerEntityTickListener(Consumer<Entity> listener) {
    if (listener == null) {
      return;
    }

    allEntityTickListeners.add(listener);
  }

  public void unregisterEntityTickListener(Consumer<Entity> listener) {
    if (listener == null) {
      return;
    }

    if (allEntityTickListeners != null) {
      allEntityTickListeners.removeIf(registered -> registered == listener);
    }

    if (entityTickListeners == null) {
      return;
    }

    for (Map.Entry<EntityType, List<Consumer<Entity>>> entry : entityTickListeners.entrySet()) {
      List<Consumer<Entity>> listeners = entry.getValue();
      if (listeners == null) {
        continue;
      }

      listeners.removeIf(registered -> registered == listener);
      if (listeners.isEmpty()) {
        entityTickListeners.remove(entry.getKey(), listeners);
      }
    }
  }

  @Override
  public String getName() {
    return "Entity";
  }

  @Override
  public String getId() {
    return "entity";
  }

  @Override
  public void start() {
    lifecycleGeneration.incrementAndGet();
    entityScanQueued.set(false);
    foliaPlayerSnapshotQueued.set(false);
    foliaScanFlight.set(null);
    nextFoliaPlayer.set(0);
    foliaPlayers = new Player[0];
    foliaPlayerSnapshotAtMS = 0L;
    allEntityTickListeners = new CopyOnWriteArrayList<>();
    entityTickListeners = new ConcurrentHashMap<>();
    EntityKiller.startAccepting();
    ReactEntity.resumeManagedCleanup();
    ReactConfiguration.get().getPriority().rebuildPriority();
    looper = new Looper() {
      @Override
      protected long loop() {
        if (!React.instance.isReady()) {
          return 100;
        }

        onTick();
        return 50;
      }
    };
  }

  public void tickEntity(Entity e) {
    if (e == null) {
      return;
    }

    if (!hasRegisteredTickListeners()) {
      return;
    }

    tickEntity(e, ReactConfiguration.get().getPriority());
  }

  private void tickEntity(Entity e, EntityPriority priority) {
    if (J.isFoliaThreading()) {
      if (!J.isOwnedByCurrentRegion(e)) {
        J.runEntity(e, () -> tickEntity(e, priority));
        return;
      }
    } else if (!J.isPrimaryThread()) {
      J.s(() -> tickEntity(e, priority));
      return;
    }

    if (!ReactEntity.tick(e, priority)) {
      return;
    }

    for (Consumer<Entity> listener : allEntityTickListeners) {
      try {
        listener.accept(e);
      } catch (Throwable ex) {
        React.reportError(ex);
      }
    }

    List<Consumer<Entity>> typeListeners = entityTickListeners.get(e.getType());
    if (typeListeners == null || typeListeners.isEmpty()) {
      return;
    }

    for (Consumer<Entity> listener : typeListeners) {
      try {
        listener.accept(e);
      } catch (Throwable ex) {
        React.reportError(ex);
      }
    }
  }

  @EventHandler
  public void on(EntitySpawnEvent e) {
    ProtectionGuards.hydrate(e.getEntity());
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityDamageEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityTargetEvent e) {
    tickEntity(e.getEntity());

    if (e.getTarget() != null) {
      tickEntity(e.getTarget());
    }
  }

  @EventHandler
  public void on(EntityInteractEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(PlayerInteractAtEntityEvent e) {
    tickEntity(e.getRightClicked());
  }

  @EventHandler
  public void on(EntityPoseChangeEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityRegainHealthEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityTameEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityPlaceEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityDropItemEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(PlayerDropItemEvent e) {
    tickEntity(e.getItemDrop());
  }

  @EventHandler
  public void on(ItemMergeEvent e) {
    tickEntity(e.getTarget());
  }

  @EventHandler
  public void on(EntityBreedEvent e) {
    tickEntity(e.getMother());
    tickEntity(e.getFather());
  }

  @EventHandler
  public void on(EntitiesLoadEvent e) {
    for (Entity entity : e.getEntities()) {
      ProtectionGuards.hydrate(entity);
      EntityKiller.reconcile(entity);
      ReactEntity.reconcileManagedState(entity);
    }
  }

  @EventHandler
  public void on(PlayerJoinEvent event) {
    if (!J.isFoliaThreading()) {
      return;
    }

    Player player = event.getPlayer();
    foliaPlayerSnapshotAtMS = 0L;
    J.runEntity(player, () -> reconcileNearbyManagedState(player));
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    foliaPlayerSnapshotAtMS = 0L;
  }

  @Override
  public void stop() {
    lifecycleGeneration.incrementAndGet();
    entityScanQueued.set(false);
    foliaPlayerSnapshotQueued.set(false);
    foliaPlayers = new Player[0];
    FoliaScanFlight scanFlight = foliaScanFlight.getAndSet(null);
    boolean foliaScanDrained = scanFlight == null
        || scanFlight.cancelAndAwaitRunning(MANAGED_CLEANUP_TIMEOUT_MS);
    if (looper != null) {
      looper.interrupt();
    }

    if (allEntityTickListeners != null) {
      allEntityTickListeners.clear();
    }
    if (entityTickListeners != null) {
      entityTickListeners.clear();
    }

    boolean killerStateDrained = EntityKiller.stopAll(MANAGED_CLEANUP_TIMEOUT_MS);
    ReactEntity.releaseAllManagedState();
    boolean managedStateDrained = ReactEntity.awaitManagedCleanup(MANAGED_CLEANUP_TIMEOUT_MS);
    ReactEntity.clearScratch();
    if (!foliaScanDrained || !killerStateDrained || !managedStateDrained) {
      throw new IllegalStateException(
          "Entity cleanup did not drain within " + MANAGED_CLEANUP_TIMEOUT_MS + "ms"
      );
    }
  }

  @Override
  public void postStart() {
    looper.start();
  }

  public void onTick() {
    EntityPriority priority = ReactConfiguration.get().getPriority();

    if (valueSaver.flip() && priority.isUseItemStackValueSystem()) {
      MaterialValue.save();
    }

    if (scratchSweeper.flip()) {
      ReactEntity.sweepScratch();
      ProtectionGuards.sweep(System.currentTimeMillis());
    }

    requestFoliaPlayerSnapshot();

    if (J.isFoliaThreading()) {
      if (hasRegisteredTickListeners()) {
        onFoliaTick(priority, lifecycleGeneration.get());
      }
      return;
    }

    if (!entityScanQueued.compareAndSet(false, true)) {
      return;
    }

    long generation = lifecycleGeneration.get();
    J.s(() -> {
      try {
        if (generation != lifecycleGeneration.get()) {
          return;
        }
        reconcileLoadedEntityState(generation);
        if (!hasRegisteredTickListeners()) {
          return;
        }

        for (World world : Bukkit.getWorlds()) {
          List<Entity> entities = WorldEntitySnapshots.next(world, Math.max(1, perWorldUpdatesPerTick));
          for (Entity sampled : entities) {
            if (generation != lifecycleGeneration.get()) {
              return;
            }
            tickEntity(sampled, priority);
          }
        }
      } finally {
        if (generation == lifecycleGeneration.get()) {
          entityScanQueued.set(false);
        }
      }
    });
  }

  private void onFoliaTick(EntityPriority priority, long generation) {
    Player[] players = foliaPlayers;
    if (players.length == 0 || generation != lifecycleGeneration.get()) {
      return;
    }

    int samples = Math.min(players.length, Math.max(1, perWorldUpdatesPerTick));
    FoliaScanFlight flight = new FoliaScanFlight(generation);
    if (!foliaScanFlight.compareAndSet(null, flight)) {
      return;
    }
    if (generation != lifecycleGeneration.get()) {
      foliaScanFlight.compareAndSet(flight, null);
      flight.cancelAndAwaitRunning(0L);
      return;
    }

    int start = Math.floorMod(nextFoliaPlayer.getAndAdd(samples), players.length);

    for (int i = 0; i < samples; i++) {
      Player player = players[(start + i) % players.length];
      if (player == null || !flight.tryRegisterTask()) {
        continue;
      }

      AtomicBoolean taskCompleted = new AtomicBoolean(false);
      Runnable completed = () -> {
        if (taskCompleted.compareAndSet(false, true)) {
          finishFoliaTask(flight);
        }
      };
      try {
        boolean scheduled = J.runEntity(
            player,
            () -> {
              try {
                sampleAroundPlayer(player, priority, flight);
              } finally {
                completed.run();
              }
            },
            0,
            completed
        );
        if (!scheduled) {
          completed.run();
        }
      } catch (Throwable throwable) {
        completed.run();
        React.reportError(throwable);
      }
    }
    if (flight.seal()) {
      foliaScanFlight.compareAndSet(flight, null);
    }
  }

  private void sampleAroundPlayer(Player player, EntityPriority priority, FoliaScanFlight flight) {
    if (!flight.tryStartOperation(lifecycleGeneration.get())) {
      return;
    }

    try {
      if (player == null
          || !player.isOnline()
          || !J.isOwnedByCurrentRegion(player)
          || !flight.isCurrent(lifecycleGeneration.get())) {
        return;
      }

      List<Entity> nearby = player.getNearbyEntities(48, 32, 48);
      if (nearby == null || nearby.isEmpty()) {
        return;
      }

      Entity sampled = nearby.get(ThreadLocalRandom.current().nextInt(nearby.size()));
      if (sampled != null
          && J.isOwnedByCurrentRegion(sampled)
          && flight.isCurrent(lifecycleGeneration.get())) {
        tickEntity(sampled, priority);
      }
    } finally {
      flight.finishOperation();
    }
  }

  private void finishFoliaTask(FoliaScanFlight flight) {
    if (flight.completeTask()) {
      foliaScanFlight.compareAndSet(flight, null);
    }
  }

  void requestFoliaPlayerSnapshot() {
    long now = System.currentTimeMillis();
    if (now - foliaPlayerSnapshotAtMS < FOLIA_PLAYER_SNAPSHOT_INTERVAL_MS
        || !foliaPlayerSnapshotQueued.compareAndSet(false, true)) {
      return;
    }

    long generation = lifecycleGeneration.get();
    boolean scheduled;
    try {
      scheduled = FoliaScheduler.runGlobal(React.instance, () -> captureFoliaPlayers(generation));
    } catch (Throwable throwable) {
      foliaPlayerSnapshotQueued.set(false);
      React.reportError(throwable);
      return;
    }
    if (!scheduled) {
      foliaPlayerSnapshotQueued.set(false);
    }
  }

  private void captureFoliaPlayers(long generation) {
    try {
      if (generation != lifecycleGeneration.get()) {
        return;
      }
      Player[] snapshot = Bukkit.getOnlinePlayers().toArray(Player[]::new);
      if (generation == lifecycleGeneration.get()) {
        foliaPlayers = snapshot;
        foliaPlayerSnapshotAtMS = System.currentTimeMillis();
      }
    } catch (Throwable throwable) {
      React.reportError(throwable);
    } finally {
      if (generation == lifecycleGeneration.get()) {
        foliaPlayerSnapshotQueued.set(false);
      }
    }
  }

  private void reconcileLoadedEntityState(long generation) {
    List<Entity> entities = WorldEntitySnapshots.reconcileNextLoadedChunks(
        INITIAL_RECONCILE_CHUNKS_PER_TICK,
        INITIAL_RECONCILE_ENTITIES_PER_TICK
    );
    for (Entity entity : entities) {
      if (generation != lifecycleGeneration.get()) {
        return;
      }
      try {
        EntityKiller.reconcile(entity);
        ReactEntity.reconcileManagedState(entity);
      } catch (Throwable throwable) {
        React.reportError(throwable);
      }
    }
  }

  private void reconcileNearbyManagedState(Player player) {
    if (player == null || !player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
      return;
    }

    EntityKiller.reconcile(player);
    ReactEntity.reconcileManagedState(player);
    for (Entity entity : player.getNearbyEntities(48, 32, 48)) {
      if (entity == null) {
        continue;
      }

      if (J.isOwnedByCurrentRegion(entity)) {
        EntityKiller.reconcile(entity);
        ReactEntity.reconcileManagedState(entity);
      } else {
        J.runEntity(entity, () -> {
          EntityKiller.reconcile(entity);
          ReactEntity.reconcileManagedState(entity);
        });
      }
    }
  }

  private boolean hasRegisteredTickListeners() {
    if (!allEntityTickListeners.isEmpty()) {
      return true;
    }

    for (List<Consumer<Entity>> listeners : entityTickListeners.values()) {
      if (listeners != null && !listeners.isEmpty()) {
        return true;
      }
    }

    return false;
  }

  private static final class FoliaScanFlight {
    private final long generation;
    private final AtomicInteger pendingTasks = new AtomicInteger(1);
    private boolean canceled;
    private boolean sealed;
    private int runningOperations;

    private FoliaScanFlight(long generation) {
      this.generation = generation;
    }

    private synchronized boolean tryRegisterTask() {
      if (canceled || sealed) {
        return false;
      }
      pendingTasks.incrementAndGet();
      return true;
    }

    private synchronized boolean tryStartOperation(long currentGeneration) {
      if (canceled || generation != currentGeneration) {
        return false;
      }
      runningOperations++;
      return true;
    }

    private synchronized boolean isCurrent(long currentGeneration) {
      return !canceled && generation == currentGeneration;
    }

    private synchronized void finishOperation() {
      runningOperations--;
      notifyAll();
    }

    private boolean completeTask() {
      return pendingTasks.decrementAndGet() == 0 && isSealed();
    }

    private synchronized boolean seal() {
      sealed = true;
      return pendingTasks.decrementAndGet() == 0;
    }

    private synchronized boolean isSealed() {
      return sealed;
    }

    private synchronized boolean cancelAndAwaitRunning(long timeoutMillis) {
      canceled = true;
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
      while (runningOperations > 0) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
          return false;
        }

        try {
          TimeUnit.NANOSECONDS.timedWait(this, remaining);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
      return true;
    }
  }
}
