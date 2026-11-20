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

package art.arcane.react.content.tweak;

import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.platform.EntityEventHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.VehicleHost;
import art.arcane.react.util.common.scheduling.J;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Vehicle Idle Brake tweak. Applies braking to distant idle vehicles to reduce runaway movement and physics overhead.")
public class TweakVehicleIdleBrake extends ReactTweak {
  private static final int MAX_TRACKED_VEHICLES = 16_384;
  private static final int MAX_VEHICLE_BUDGET = 4096;

  public static final String ID = "vehicle-idle-brake";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for vehicle idle brake in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum vehicles sampled per evaluation across all worlds in vehicle idle brake, clamped from 1 to 4096.", impact = "Higher values inspect more vehicles per cycle; lower values place a tighter bound on evaluation work.")
  private int maxVehiclesSampledPerWorld = 180;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum velocity squared required by vehicle idle brake.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private double minVelocitySquared = 0.0004;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum distance without player allowed by vehicle idle brake.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxDistanceWithoutPlayer = 48;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether vehicle idle brake applies only empty vehicles.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean onlyEmptyVehicles = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether vehicle idle brake applies brake minecarts.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean brakeMinecarts = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether vehicle idle brake applies brake boats.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean brakeBoats = true;
  private transient final AtomicInteger nextFoliaAnchor = new AtomicInteger(0);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final AtomicReference<ScanCycle> activeScan = new AtomicReference<>();
  private transient final Map<UUID, WeakReference<Object>> indexedVehicles = new ConcurrentHashMap<>();
  private transient final Queue<UUID> indexedVehicleOrder = new ConcurrentLinkedQueue<>();
  private transient final Consumer<GameEntity> entityTickListener = this::indexVehicle;
  private transient final Object lifecycleLock = new Object();
  private transient volatile long activeGeneration;
  private transient Subscription ticks = Subscription.NONE;
  private transient volatile VehicleHost vehicles;
  private transient volatile EntityEventHost events;

  public TweakVehicleIdleBrake() {
    super(ID);
  }

  @Override
  public void onActivate() {
    synchronized (lifecycleLock) {
      activeGeneration = lifecycleGeneration.incrementAndGet();
      activeScan.set(null);
      nextFoliaAnchor.set(0);
      indexedVehicles.clear();
      indexedVehicleOrder.clear();
    }

    if (!J.isFoliaThreading()) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      EntityHost host = platform == null ? null : platform.entityHost().orElse(null);
      if (host != null) {
        ticks = host.onTick(List.of(), entityTickListener);
      }
    }
  }

  @Override
  public void onDeactivate() {
    synchronized (lifecycleLock) {
      activeGeneration = 0L;
      lifecycleGeneration.incrementAndGet();
      activeScan.set(null);
    }

    ticks.cancel();
    ticks = Subscription.NONE;
    indexedVehicles.clear();
    indexedVehicleOrder.clear();
  }

  @Override
  public int getTickInterval() {
    return Math.max(50, tickIntervalMS);
  }

  @Override
  public void onTick() {
    long generation = activeGeneration;
    if (generation == 0L) {
      return;
    }

    ScanCycle cycle = new ScanCycle(generation, vehicleBudget(maxVehiclesSampledPerWorld));
    if (!activeScan.compareAndSet(null, cycle)) {
      return;
    }

    if (J.isFoliaThreading()) {
      scanPlayersFolia(cycle);
      return;
    }

    cycle.setPendingTasks(1);
    AtomicBoolean taskCompleted = new AtomicBoolean(false);
    Runnable completed = () -> {
      if (taskCompleted.compareAndSet(false, true)) {
        completeScan(cycle);
      }
    };
    try {
      J.s(() -> {
        try {
          scanIndexedVehicles(cycle);
        } finally {
          completed.run();
        }
      });
    } catch (Throwable throwable) {
      completed.run();
      ReactPlatform platform = ReactPlatforms.getOrNull();
      if (platform != null) {
        platform.reportError(throwable);
      }
    }
  }

  private void scanPlayersFolia(ScanCycle cycle) {
    VehicleHost host = vehicles();
    List<GameEntity> players = host == null ? List.of() : host.anchors();
    int anchorCount = foliaAnchorCount(players.size(), cycle.remainingVehicles());
    if (anchorCount == 0 || !isActive(cycle)) {
      activeScan.compareAndSet(cycle, null);
      return;
    }

    int perAnchor = Math.max(1, (cycle.remainingVehicles() - 1) / anchorCount + 1);
    int start = Math.floorMod(nextFoliaAnchor.getAndAdd(anchorCount), players.size());
    cycle.setPendingTasks(anchorCount);
    for (int i = 0; i < anchorCount; i++) {
      GameEntity player = players.get((start + i) % players.size());
      if (player == null) {
        completeScan(cycle);
        continue;
      }

      AtomicBoolean taskCompleted = new AtomicBoolean(false);
      Runnable completed = () -> {
        if (taskCompleted.compareAndSet(false, true)) {
          completeScan(cycle);
        }
      };
      boolean scheduled = J.runEntity(player.handle(), () -> {
        try {
          sampleAndBrakeAroundPlayer(host, player, cycle, perAnchor);
        } finally {
          completed.run();
        }
      }, 0, completed);
      if (!scheduled) {
        completed.run();
      }
    }
  }

  private void sampleAndBrakeAroundPlayer(VehicleHost host, GameEntity player, ScanCycle cycle, int perAnchor) {
    if (!isActive(cycle)
        || !host.isOnline(player)
        || !J.isOwnedByCurrentRegion(player.handle())
        || cycle.remainingVehicles() <= 0) {
      return;
    }

    List<GameEntity> nearby = host.sampleNear(
        player,
        ID,
        maxDistanceWithoutPlayer + 24,
        Math.max(32, maxDistanceWithoutPlayer),
        perAnchor,
        entity -> claimNearbyVehicle(entity, cycle)
    );
    for (GameEntity entity : nearby) {
      if (!isActive(cycle)) {
        return;
      }
      brakeIfIdle(entity, cycle);
    }
  }

  private boolean claimNearbyVehicle(GameEntity entity, ScanCycle cycle) {
    return isActive(cycle)
        && cycle.remainingVehicles() > 0
        && isBrakeableVehicle(entity)
        && J.isOwnedByCurrentRegion(entity.handle())
        && cycle.claimVehicle(entity.id());
  }

  private boolean isBrakeableVehicle(GameEntity entity) {
    VehicleHost host = vehicles();
    return host != null && ((brakeMinecarts && host.isMinecart(entity)) || (brakeBoats && host.isBoat(entity)));
  }

  private void indexVehicle(GameEntity entity) {
    if (activeGeneration == 0L || entity == null || !isBrakeableVehicle(entity)) {
      return;
    }

    UUID entityId = entity.id();
    Object handle = entity.handle();
    WeakReference<Object> reference = new WeakReference<>(handle);
    WeakReference<Object> existing = indexedVehicles.putIfAbsent(entityId, reference);
    if (existing == null) {
      if (indexedVehicles.size() > MAX_TRACKED_VEHICLES) {
        indexedVehicles.remove(entityId, reference);
        return;
      }
      indexedVehicleOrder.offer(entityId);
      return;
    }

    if (existing.get() != handle) {
      indexedVehicles.replace(entityId, existing, reference);
    }
  }

  private void scanIndexedVehicles(ScanCycle cycle) {
    int inspections = Math.min(cycle.remainingVehicles(), indexedVehicles.size());
    for (int i = 0; i < inspections; i++) {
      if (!isActive(cycle) || cycle.remainingVehicles() <= 0) {
        return;
      }

      UUID entityId = indexedVehicleOrder.poll();
      if (entityId == null) {
        return;
      }

      WeakReference<Object> reference = indexedVehicles.get(entityId);
      Object handle = reference == null ? null : reference.get();
      VehicleHost host = vehicles();
      GameEntity vehicle = handle == null || host == null ? null : host.vehicle(handle);
      if (vehicle == null || !vehicle.isValid()) {
        if (reference != null) {
          indexedVehicles.remove(entityId, reference);
        }
        continue;
      }

      indexedVehicleOrder.offer(entityId);
      if (cycle.claimVehicle(entityId)) {
        brakeIfIdle(vehicle, cycle);
      }
    }
  }

  private void brakeIfIdle(GameEntity vehicle, ScanCycle cycle) {
    VehicleHost host = vehicles();
    if (host == null || !shouldBrake(host, vehicle, cycle)) {
      return;
    }

    synchronized (lifecycleLock) {
      if (!isActive(cycle)) {
        return;
      }

      host.stop(vehicle);
    }
  }

  private boolean shouldBrake(VehicleHost host, GameEntity vehicle, ScanCycle cycle) {
    if (!isActive(cycle)) {
      return false;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(vehicle.handle())) {
      return false;
    }

    if (!vehicle.isValid()) {
      return false;
    }

    if (onlyEmptyVehicles && host.hasPassengers(vehicle)) {
      return false;
    }

    if (host.velocitySquared(vehicle) < minVelocitySquared) {
      return false;
    }

    EntityEventHost checks = events();
    return checks != null && !checks.nearPlayer(vehicle, maxDistanceWithoutPlayer);
  }

  private VehicleHost vehicles() {
    VehicleHost host = vehicles;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.vehicleHost().orElse(null);
      vehicles = host;
    }
    return host;
  }

  private EntityEventHost events() {
    EntityEventHost host = events;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.entityEventHost().orElse(null);
      events = host;
    }
    return host;
  }

  private boolean isActive(ScanCycle cycle) {
    return cycle.generation() == activeGeneration;
  }

  private void completeScan(ScanCycle cycle) {
    if (cycle.completeTask()) {
      activeScan.compareAndSet(cycle, null);
    }
  }

  static int foliaAnchorCount(int players, int budget) {
    if (players <= 0 || budget <= 0) {
      return 0;
    }
    return Math.min(players, Math.max(1, (budget - 1) / 12 + 1));
  }

  static int vehicleBudget(int configuredBudget) {
    return Math.max(1, Math.min(configuredBudget, MAX_VEHICLE_BUDGET));
  }

  private static final class ScanCycle {
    private final long generation;
    private final AtomicInteger remainingVehicles;
    private final Set<UUID> sampledVehicles;
    private final AtomicInteger pendingTasks;

    private ScanCycle(long generation, int budget) {
      this.generation = generation;
      remainingVehicles = new AtomicInteger(budget);
      sampledVehicles = ConcurrentHashMap.newKeySet(Math.max(1, budget));
      pendingTasks = new AtomicInteger(0);
    }

    private long generation() {
      return generation;
    }

    private int remainingVehicles() {
      return remainingVehicles.get();
    }

    private boolean claimVehicle(UUID vehicleId) {
      if (!sampledVehicles.add(vehicleId)) {
        return false;
      }

      int remaining = remainingVehicles.get();
      while (remaining > 0) {
        if (remainingVehicles.compareAndSet(remaining, remaining - 1)) {
          return true;
        }
        remaining = remainingVehicles.get();
      }
      return false;
    }

    private void setPendingTasks(int tasks) {
      pendingTasks.set(tasks);
    }

    private boolean completeTask() {
      return pendingTasks.decrementAndGet() == 0;
    }
  }
}
