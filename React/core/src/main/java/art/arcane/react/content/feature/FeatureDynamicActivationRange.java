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

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.content.feature.perworld.PerWorldPressure;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.platform.EntityActivityHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Dynamic Activation Range feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureDynamicActivationRange extends ReactFeature {
  public static final String ID = "dynamic-activation-range";
  private transient final AtomicBoolean activationRangeScanQueued = new AtomicBoolean(false);
  private transient final AtomicInteger nextFoliaAnchor = new AtomicInteger(0);
  private transient final AtomicInteger nextWorldSample = new AtomicInteger(0);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final Object lifecycleLock = new Object();
  private transient volatile boolean active;
  private transient volatile WorldHost worlds;
  private transient volatile EntityHost entities;
  private transient volatile EntityActivityHost activity;
  private transient volatile Subscription wakeHooks = Subscription.NONE;
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
      nextFoliaAnchor.set(0);
      nextWorldSample.set(0);
      currentActivationRange = maximumActivationRange;
      wakeHooks.cancel();
      EntityActivityHost host = activity();
      wakeHooks = host == null ? Subscription.NONE : subscribeWakeHooks(host);
      active = true;
    }
  }

  private Subscription subscribeWakeHooks(EntityActivityHost host) {
    Subscription damage = host.onDamage(this::onDamage);
    Subscription target = host.onTarget(this::onTarget);
    return () -> {
      damage.cancel();
      target.cancel();
    };
  }

  @Override
  public void onDeactivate() {
    synchronized (lifecycleLock) {
      active = false;
      lifecycleGeneration.incrementAndGet();
      activationRangeScanQueued.set(false);
      Subscription hooks = wakeHooks;
      wakeHooks = Subscription.NONE;
      hooks.cancel();
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
    double tickTime = sample(SamplerTickTime.ID);
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
          reportError(throwable);
        } finally {
          flight.complete();
        }
      });
    } catch (Throwable throwable) {
      flight.complete();
      reportError(throwable);
    }
  }

  private void tuneRange(double tickTime) {
    currentActivationRange = tunedRange(currentActivationRange, tickTime, targetTickMS, criticalTickMS,
        minimumActivationRange, maximumActivationRange);
  }

  static double tunedRange(double current, double tickTime, double targetTickMs, double criticalTickMs,
                           double minimum, double maximum) {
    if (tickTime > criticalTickMs) {
      return Math.max(minimum, current - 6);
    }

    if (tickTime > targetTickMs) {
      return Math.max(minimum, current - 2);
    }

    return Math.min(maximum, current + 1);
  }

  private void applyActivationRange(long generation, double activationRange) {
    if (generation != lifecycleGeneration.get()) {
      return;
    }

    int budget = Math.max(1, maxEntitiesSampledPerCycle);
    WorldHost worldHost = worlds();
    EntityActivityHost host = activity();
    List<ReactWorld> loaded = worldHost == null ? List.of() : worldHost.worlds();
    if (loaded.isEmpty() || host == null) {
      return;
    }
    int worldStart = Math.floorMod(nextWorldSample.getAndIncrement(), loaded.size());

    for (int worldIndex = 0; worldIndex < loaded.size(); worldIndex++) {
      if (generation != lifecycleGeneration.get() || budget <= 0) {
        return;
      }

      ReactWorld world = loaded.get((worldStart + worldIndex) % loaded.size());
      List<GameEntity> sampled = host.nextEntities(world, budget);
      if (sampled.isEmpty()) {
        continue;
      }

      for (GameEntity entity : sampled) {
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
    WorldHost worldHost = worlds();
    List<ReactPlayer> players = worldHost == null ? null : worldHost.onlinePlayers();
    if (generation != lifecycleGeneration.get() || players == null || players.isEmpty()) {
      flight.complete();
      return;
    }

    int budget = Math.max(1, maxEntitiesSampledPerCycle);
    int playerSamples = Math.min(players.size(), Math.max(1, ((budget - 1) / 8) + 1));
    int quota = budget / playerSamples;
    int extra = budget % playerSamples;
    int start = Math.floorMod(nextFoliaAnchor.getAndAdd(playerSamples), players.size());
    Set<UUID> sampledEntities = ConcurrentHashMap.newKeySet();

    for (int i = 0; i < playerSamples; i++) {
      ReactPlayer player = players.get((start + i) % players.size());
      int playerQuota = quota + (i < extra ? 1 : 0);
      flight.schedule(player.handle(), () -> sampleAroundPlayer(
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
      ReactPlayer player,
      int quota,
      Set<UUID> sampledEntities,
      ScanFlight flight,
      long generation,
      double activationRange
  ) {
    WorldHost worldHost = worlds();
    if (generation != lifecycleGeneration.get()
        || player == null
        || worldHost == null
        || !worldHost.isOnline(player)
        || !J.isOwnedByCurrentRegion(player.handle())) {
      return;
    }

    EntityActivityHost host = activity();
    if (host == null) {
      return;
    }
    List<GameEntity> nearby = host.sampleNearby(
        player, ID, activationRange + 16, Math.max(32, activationRange), quota, null
    );
    if (nearby.isEmpty()) {
      return;
    }

    int sample = Math.min(nearby.size(), quota);
    for (int i = 0; i < sample; i++) {
      if (generation != lifecycleGeneration.get()) {
        return;
      }

      GameEntity entity = nearby.get(i);
      if (entity == null) {
        continue;
      }

      manage(entity, generation, activationRange, flight, sampledEntities);
    }
  }

  private void manage(
      GameEntity entity,
      long generation,
      double activationRange,
      ScanFlight flight,
      Set<UUID> sampledEntities
  ) {
    if (!isCurrent(generation) || entity == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity.handle())) {
      if (flight != null) {
        flight.schedule(entity.handle(), () -> manage(entity, generation, activationRange, flight, sampledEntities));
      }
      return;
    }

    if (sampledEntities != null) {
      UUID entityId = entity.id();
      if (entityId == null || !sampledEntities.add(entityId)) {
        return;
      }
    }

    if (!canManage(entity)) {
      wake(entity, generation);
      return;
    }

    double effectiveRange = activationRange;
    GameLocation location = entity.location();
    PerWorldPressure pressure = location == null ? PerWorldPressure.NORMAL : PerWorldPressure.get(location.worldId());
    if (pressure.isPanic()) {
      effectiveRange = minimumActivationRange;
    } else if (pressure.isPressure()) {
      effectiveRange = Math.max(minimumActivationRange, activationRange * 0.5D);
    }

    WorldHost worldHost = worlds();
    if (worldHost != null && worldHost.anyPlayerNear(location, effectiveRange)) {
      wake(entity, generation);
      return;
    }

    synchronized (lifecycleLock) {
      if (isCurrent(generation)) {
        ReactEntity.requestPause(entity, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE);
      }
    }
  }

  private boolean canManage(GameEntity entity) {
    if (!entity.isLiving() || !entity.isValid() || entity.isPlayer()) {
      return false;
    }

    EntityHost host = entities();
    EntityActivityHost traits = activity();
    if (host == null || traits == null || host.isProtected(entity, ReactOperation.SLEEP)) {
      return false;
    }

    if (entity.ticksLived() < minimumEntityAgeTicks) {
      return false;
    }

    if (ignoreNamedEntities && traits.isNamed(entity)) {
      return false;
    }

    return !(ignoreTamedEntities && entity.isTamed());
  }

  private void wake(GameEntity entity) {
    if (entity != null && entity.isValid()) {
      ReactEntity.releasePause(entity, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE);
    }
  }

  private void wake(GameEntity entity, long generation) {
    synchronized (lifecycleLock) {
      if (isCurrent(generation)) {
        wake(entity);
      }
    }
  }

  private void wakeOnOwner(GameEntity entity, long generation) {
    if (!isCurrent(generation)
        || entity == null
        || !ReactEntity.isPausedBy(entity, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE)) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity.handle())) {
      J.runEntity(entity.handle(), () -> wake(entity, generation));
      return;
    }

    wake(entity, generation);
  }

  private boolean isCurrent(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  void onDamage(GameEntity entity) {
    long generation = lifecycleGeneration.get();
    wakeOnOwner(entity, generation);
  }

  void onTarget(GameEntity entity, GameEntity target) {
    long generation = lifecycleGeneration.get();
    wakeOnOwner(entity, generation);
    wakeOnOwner(target, generation);
  }

  private WorldHost worlds() {
    WorldHost host = worlds;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.worldHost().orElse(null);
      worlds = host;
    }
    return host;
  }

  private EntityHost entities() {
    EntityHost host = entities;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.entityHost().orElse(null);
      entities = host;
    }
    return host;
  }

  private EntityActivityHost activity() {
    EntityActivityHost host = activity;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.entityActivityHost().orElse(null);
      activity = host;
    }
    return host;
  }

  private static void reportError(Throwable throwable) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(throwable);
    }
  }

  private final class ScanFlight {
    private final long generation;
    private final AtomicInteger pending = new AtomicInteger(1);

    private ScanFlight(long generation) {
      this.generation = generation;
    }

    private void schedule(Object owner, Runnable work) {
      pending.incrementAndGet();
      AtomicBoolean dispatchComplete = new AtomicBoolean(false);
      Runnable completeDispatch = () -> {
        if (dispatchComplete.compareAndSet(false, true)) {
          complete();
        }
      };
      boolean scheduled = false;
      try {
        scheduled = J.runEntity(owner, () -> {
          try {
            if (isCurrent(generation)) {
              work.run();
            }
          } catch (Throwable throwable) {
            reportError(throwable);
          } finally {
            completeDispatch.run();
          }
        }, 0, completeDispatch);
      } catch (Throwable throwable) {
        reportError(throwable);
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
