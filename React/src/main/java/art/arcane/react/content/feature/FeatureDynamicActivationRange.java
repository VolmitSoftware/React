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

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactProtection;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.feature.perworld.PerWorldPressure;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Dynamic Activation Range feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureDynamicActivationRange extends ReactFeature implements Listener {
  public static final String ID = "dynamic-activation-range";
  private transient final AtomicBoolean activationRangeScanQueued = new AtomicBoolean(false);
  private transient final AtomicInteger nextEntitySample = new AtomicInteger(0);
  private transient final AtomicInteger nextFoliaAnchor = new AtomicInteger(0);
  private transient final AtomicInteger nextWorldSample = new AtomicInteger(0);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final Object lifecycleLock = new Object();
  private transient volatile boolean active;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for dynamic activation range in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entities sampled allowed per cycle in dynamic activation range.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxEntitiesSampledPerCycle = 240;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum activation range required by dynamic activation range.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private double minimumActivationRange = 18;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum activation range allowed by dynamic activation range.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maximumActivationRange = 64;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Activation radius used by dynamic activation range (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private volatile double currentActivationRange = maximumActivationRange;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for target tick ms in dynamic activation range (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double targetTickMS = 45;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for critical in dynamic activation range (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double criticalTickMS = 70;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum entity age ticks required by dynamic activation range.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private double minimumEntityAgeTicks = 100;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips tamed entities when dynamic activation range evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean ignoreTamedEntities = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips named entities when dynamic activation range evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean ignoreNamedEntities = true;

  public FeatureDynamicActivationRange() {
    super(ID);
  }

  @Override
  public void onActivate() {
    synchronized (lifecycleLock) {
      active = false;
      lifecycleGeneration.incrementAndGet();
      activationRangeScanQueued.set(false);
      nextEntitySample.set(0);
      nextFoliaAnchor.set(0);
      nextWorldSample.set(0);
      currentActivationRange = maximumActivationRange;
      active = true;
    }
  }

  @Override
  public void onDeactivate() {
    synchronized (lifecycleLock) {
      active = false;
      lifecycleGeneration.incrementAndGet();
      activationRangeScanQueued.set(false);
    }
    ReactEntity.releasePauseOwner(ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE);
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    if (!active) {
      return;
    }

    long generation = lifecycleGeneration.get();
    double tickTime = React.sampler(SamplerTickTime.ID).sample();
    double activationRange;
    synchronized (lifecycleLock) {
      if (!isCurrent(generation)) {
        return;
      }
      tuneRange(tickTime);
      activationRange = currentActivationRange;
    }

    if (!activationRangeScanQueued.compareAndSet(false, true)) {
      return;
    }
    if (!isCurrent(generation)) {
      activationRangeScanQueued.set(false);
      return;
    }

    ScanFlight flight = new ScanFlight(generation);
    if (J.isFoliaThreading()) {
      applyFoliaActivationRange(flight, generation, activationRange);
      return;
    }

    try {
      J.s(() -> {
        try {
          applyActivationRange(generation, activationRange);
        } catch (Throwable throwable) {
          React.reportError(throwable);
        } finally {
          flight.complete();
        }
      });
    } catch (Throwable throwable) {
      flight.complete();
      React.reportError(throwable);
    }
  }

  private void tuneRange(double tickTime) {
    if (tickTime > criticalTickMS) {
      currentActivationRange = Math.max(minimumActivationRange, currentActivationRange - 6);
      return;
    }

    if (tickTime > targetTickMS) {
      currentActivationRange = Math.max(minimumActivationRange, currentActivationRange - 2);
      return;
    }

    currentActivationRange = Math.min(maximumActivationRange, currentActivationRange + 1);
  }

  private void applyActivationRange(long generation, double activationRange) {
    if (generation != lifecycleGeneration.get()) {
      return;
    }

    int budget = Math.max(1, maxEntitiesSampledPerCycle);
    List<World> worlds = Bukkit.getWorlds();
    if (worlds.isEmpty()) {
      return;
    }
    int worldStart = Math.floorMod(nextWorldSample.getAndIncrement(), worlds.size());

    for (int worldIndex = 0; worldIndex < worlds.size(); worldIndex++) {
      if (generation != lifecycleGeneration.get() || budget <= 0) {
        return;
      }

      World world = worlds.get((worldStart + worldIndex) % worlds.size());
      List<Entity> entities = WorldEntitySnapshots.next(world, budget);
      if (entities.isEmpty()) {
        continue;
      }

      for (Entity entity : entities) {
        if (generation != lifecycleGeneration.get()) {
          return;
        }

        budget--;
        manage(entity, generation, activationRange, null, null);
        if (budget <= 0) {
          return;
        }
      }
    }
  }

  private void applyFoliaActivationRange(ScanFlight flight, long generation, double activationRange) {
    EntityController controller = React.controller(EntityController.class);
    Player[] players = controller == null ? null : controller.getFoliaPlayers();
    if (generation != lifecycleGeneration.get() || players == null || players.length == 0) {
      flight.complete();
      return;
    }

    int budget = Math.max(1, maxEntitiesSampledPerCycle);
    int playerSamples = Math.min(players.length, Math.max(1, ((budget - 1) / 8) + 1));
    int quota = budget / playerSamples;
    int extra = budget % playerSamples;
    int start = Math.floorMod(nextFoliaAnchor.getAndAdd(playerSamples), players.length);
    Set<UUID> sampledEntities = ConcurrentHashMap.newKeySet();

    for (int i = 0; i < playerSamples; i++) {
      Player player = players[(start + i) % players.length];
      int playerQuota = quota + (i < extra ? 1 : 0);
      flight.schedule(player, () -> sampleAroundPlayer(
          player,
          playerQuota,
          sampledEntities,
          flight,
          generation,
          activationRange
      ));
    }

    flight.complete();
  }

  private void sampleAroundPlayer(
      Player player,
      int quota,
      Set<UUID> sampledEntities,
      ScanFlight flight,
      long generation,
      double activationRange
  ) {
    if (generation != lifecycleGeneration.get()
        || player == null
        || !player.isOnline()
        || !J.isOwnedByCurrentRegion(player)) {
      return;
    }

    List<Entity> nearby = player.getNearbyEntities(
        activationRange + 16,
        Math.max(32, activationRange),
        activationRange + 16
    );
    if (nearby.isEmpty()) {
      return;
    }

    int sample = Math.min(nearby.size(), quota);
    int start = Math.floorMod(nextEntitySample.getAndAdd(sample), nearby.size());
    for (int i = 0; i < sample; i++) {
      if (generation != lifecycleGeneration.get()) {
        return;
      }

      Entity entity = nearby.get((start + i) % nearby.size());
      if (entity == null) {
        continue;
      }

      manage(entity, generation, activationRange, flight, sampledEntities);
    }
  }

  private void manage(
      Entity entity,
      long generation,
      double activationRange,
      ScanFlight flight,
      Set<UUID> sampledEntities
  ) {
    if (!isCurrent(generation) || entity == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity)) {
      if (flight != null) {
        flight.schedule(entity, () -> manage(entity, generation, activationRange, flight, sampledEntities));
      }
      return;
    }

    if (sampledEntities != null) {
      UUID entityId = entity.getUniqueId();
      if (entityId == null || !sampledEntities.add(entityId)) {
        return;
      }
    }

    if (!canManage(entity)) {
      wake(entity, generation);
      return;
    }

    double effectiveRange = activationRange;
    PerWorldPressure pressure = PerWorldPressure.get(entity.getWorld());
    if (pressure.isPanic()) {
      effectiveRange = minimumActivationRange;
    } else if (pressure.isPressure()) {
      effectiveRange = Math.max(minimumActivationRange, activationRange * 0.5D);
    }

    if (React.hasNearbyPlayer(entity.getLocation(), effectiveRange)) {
      wake(entity, generation);
      return;
    }

    synchronized (lifecycleLock) {
      if (isCurrent(generation)) {
        ReactEntity.requestPause(entity, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE);
      }
    }
  }

  private boolean canManage(Entity entity) {
    if (!(entity instanceof LivingEntity living) || entity.isDead() || entity instanceof Player) {
      return false;
    }

    if (ReactProtection.isProtected(entity, ReactOperation.SLEEP)) {
      return false;
    }

    if (entity.getTicksLived() < minimumEntityAgeTicks) {
      return false;
    }

    if (ignoreNamedEntities) {
      String customName = living.getCustomName();
      if (customName != null && !customName.isBlank()) {
        return false;
      }
    }

    return !(ignoreTamedEntities && living instanceof Tameable tameable && tameable.isTamed());
  }

  private void wake(Entity entity) {
    if (entity != null && !entity.isDead()) {
      ReactEntity.releasePause(entity, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE);
    }
  }

  private void wake(Entity entity, long generation) {
    synchronized (lifecycleLock) {
      if (isCurrent(generation)) {
        wake(entity);
      }
    }
  }

  private void wakeOnOwner(Entity entity, long generation) {
    if (!isCurrent(generation) || entity == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity)) {
      J.runEntity(entity, () -> wake(entity, generation));
      return;
    }

    wake(entity, generation);
  }

  private boolean isCurrent(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  @EventHandler(ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    long generation = lifecycleGeneration.get();
    wakeOnOwner(event.getEntity(), generation);
  }

  @EventHandler(ignoreCancelled = true)
  public void on(EntityTargetEvent event) {
    long generation = lifecycleGeneration.get();
    wakeOnOwner(event.getEntity(), generation);
    wakeOnOwner(event.getTarget(), generation);
  }

  private final class ScanFlight {
    private final long generation;
    private final AtomicInteger pending = new AtomicInteger(1);

    private ScanFlight(long generation) {
      this.generation = generation;
    }

    private void schedule(Entity entity, Runnable work) {
      pending.incrementAndGet();
      AtomicBoolean dispatchComplete = new AtomicBoolean(false);
      Runnable completeDispatch = () -> {
        if (dispatchComplete.compareAndSet(false, true)) {
          complete();
        }
      };
      boolean scheduled = false;
      try {
        scheduled = J.runEntity(entity, () -> {
          try {
            if (isCurrent(generation)) {
              work.run();
            }
          } catch (Throwable throwable) {
            React.reportError(throwable);
          } finally {
            completeDispatch.run();
          }
        }, 0, completeDispatch);
      } catch (Throwable throwable) {
        React.reportError(throwable);
      } finally {
        if (!scheduled) {
          completeDispatch.run();
        }
      }
    }

    private void complete() {
      if (pending.decrementAndGet() == 0 && isCurrent(generation)) {
        activationRangeScanQueued.set(false);
      }
    }
  }
}
