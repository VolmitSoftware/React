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

import art.arcane.react.React;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
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
  private transient final Map<UUID, WeakReference<Entity>> indexedVehicles = new ConcurrentHashMap<>();
  private transient final Queue<UUID> indexedVehicleOrder = new ConcurrentLinkedQueue<>();
  private transient final Consumer<Entity> entityTickListener = this::indexVehicle;
  private transient final Object lifecycleLock = new Object();
  private transient volatile long activeGeneration;

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
      EntityController controller = React.controller(EntityController.class);
      if (controller != null) {
        controller.registerEntityTickListener(entityTickListener);
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

    EntityController controller = React.controller(EntityController.class);
    if (controller != null) {
      controller.unregisterEntityTickListener(entityTickListener);
    }
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
      React.reportError(throwable);
    }
  }

  private void scanPlayersFolia(ScanCycle cycle) {
    Player[] players = playerSnapshot();
    int anchorCount = foliaAnchorCount(players.length, cycle.remainingVehicles());
    if (anchorCount == 0 || !isActive(cycle)) {
      activeScan.compareAndSet(cycle, null);
      return;
    }

    int perAnchor = Math.max(1, (cycle.remainingVehicles() - 1) / anchorCount + 1);
    int start = Math.floorMod(nextFoliaAnchor.getAndAdd(anchorCount), players.length);
    cycle.setPendingTasks(anchorCount);
    for (int i = 0; i < anchorCount; i++) {
      Player player = players[(start + i) % players.length];
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
      boolean scheduled = J.runEntity(player, () -> {
        try {
          sampleAndBrakeAroundPlayer(player, cycle, perAnchor);
        } finally {
          completed.run();
        }
      }, 0, completed);
      if (!scheduled) {
        completed.run();
      }
    }
  }

  private void sampleAndBrakeAroundPlayer(Player player, ScanCycle cycle, int perAnchor) {
    if (!isActive(cycle)
        || !player.isOnline()
        || !J.isOwnedByCurrentRegion(player)
        || cycle.remainingVehicles() <= 0) {
      return;
    }

    List<Entity> nearby = player.getNearbyEntities(
        maxDistanceWithoutPlayer + 24,
        Math.max(32, maxDistanceWithoutPlayer),
        maxDistanceWithoutPlayer + 24
    );
    if (nearby.isEmpty()) {
      return;
    }

    int start = ThreadLocalRandom.current().nextInt(nearby.size());
    int inspectionLimit = Math.min(nearby.size(), Math.max(64, perAnchor * 8));
    int sampled = 0;
    for (int i = 0; i < inspectionLimit && sampled < perAnchor; i++) {
      if (!isActive(cycle) || cycle.remainingVehicles() <= 0) {
        return;
      }

      Entity entity = nearby.get((start + i) % nearby.size());
      if (!isBrakeableVehicle(entity)
          || !J.isOwnedByCurrentRegion(entity)
          || !cycle.claimVehicle(entity.getUniqueId())) {
        continue;
      }

      sampled++;
      brakeIfIdle(entity, cycle);
    }
  }

  private boolean isBrakeableVehicle(Entity entity) {
    return (brakeMinecarts && entity instanceof Minecart) || (brakeBoats && entity instanceof Boat);
  }

  private void indexVehicle(Entity entity) {
    if (activeGeneration == 0L || entity == null || !isBrakeableVehicle(entity)) {
      return;
    }

    UUID entityId = entity.getUniqueId();
    WeakReference<Entity> reference = new WeakReference<>(entity);
    WeakReference<Entity> existing = indexedVehicles.putIfAbsent(entityId, reference);
    if (existing == null) {
      if (indexedVehicles.size() > MAX_TRACKED_VEHICLES) {
        indexedVehicles.remove(entityId, reference);
        return;
      }
      indexedVehicleOrder.offer(entityId);
      return;
    }

    if (existing.get() != entity) {
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

      WeakReference<Entity> reference = indexedVehicles.get(entityId);
      Entity vehicle = reference == null ? null : reference.get();
      if (vehicle == null || vehicle.isDead()) {
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

  private void brakeIfIdle(Entity vehicle, ScanCycle cycle) {
    if (!shouldBrake(vehicle, cycle)) {
      return;
    }

    synchronized (lifecycleLock) {
      if (!isActive(cycle)) {
        return;
      }

      vehicle.setVelocity(new Vector(0, 0, 0));
      if (vehicle instanceof Minecart minecart) {
        minecart.setDerailedVelocityMod(new Vector(0, 0, 0));
        minecart.setFlyingVelocityMod(new Vector(0, 0, 0));
      }
    }
  }

  private boolean shouldBrake(Entity vehicle, ScanCycle cycle) {
    if (!isActive(cycle)) {
      return false;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(vehicle)) {
      return false;
    }

    if (vehicle.isDead()) {
      return false;
    }

    if (onlyEmptyVehicles && !vehicle.getPassengers().isEmpty()) {
      return false;
    }

    if (vehicle.getVelocity().lengthSquared() < minVelocitySquared) {
      return false;
    }

    return !React.hasNearbyPlayer(vehicle.getLocation(), maxDistanceWithoutPlayer);
  }

  private Player[] playerSnapshot() {
    EntityController controller = React.controller(EntityController.class);
    return controller == null ? new Player[0] : controller.getFoliaPlayers();
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
