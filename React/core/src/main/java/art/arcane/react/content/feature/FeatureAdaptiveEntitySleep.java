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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Adaptive Entity Sleep feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureAdaptiveEntitySleep extends ReactFeature {
  static boolean shouldDoze(double lastTickMs, double dutyCycleMinTickMs, int dutyCycleSlots, int entityId, int dutyCycleIndex) {
    if (lastTickMs < dutyCycleMinTickMs) {
      return false;
    }

    return Math.floorMod(entityId + dutyCycleIndex, Math.max(2, dutyCycleSlots)) != 0;
  }

  public static final String ID = "adaptive-entity-sleep";
  private transient final AtomicLong sleepScanGeneration = new AtomicLong(0L);
  private transient final AtomicInteger nextPaperWorld = new AtomicInteger(0);
  private transient final AtomicInteger nextFoliaAnchor = new AtomicInteger(0);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final AtomicReference<FoliaScanFlight> foliaScanFlight = new AtomicReference<>();
  private transient final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock(true);
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for adaptive entity sleep in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entities sampled allowed per cycle in adaptive entity sleep.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxEntitiesSampledPerCycle = 320;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum entity age ticks required by adaptive entity sleep.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private int minimumEntityAgeTicks = 200;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Distance from players required before adaptive entity sleep puts entities into sleep mode (blocks).", impact = "Higher values sleep entities farther away; lower values keep more entities active near players.")
  private double sleepBeyondNearestPlayer = 48;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips named entities when adaptive entity sleep evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean ignoreNamedEntities = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips tamed entities when adaptive entity sleep evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean ignoreTamedEntities = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips persistent entities when adaptive entity sleep evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean ignorePersistentEntities = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips villagers when adaptive entity sleep evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean ignoreVillagers = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips bosses when adaptive entity sleep evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean ignoreBosses = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether adaptive entity sleep applies wake on damage.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean wakeOnDamage = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether adaptive entity sleep applies wake on target.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean wakeOnTarget = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Duty-cycles mob awareness for mobs between the duty-cycle distance and the sleep distance while the server is under load.", impact = "Enable to shed mid-distance mob AI/pathfinding cost under load; disable to keep the original binary sleep behavior only.")
  private boolean dutyCycleEnabled = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Distance from players where duty-cycling begins (blocks). Mobs closer than this always keep full AI.", impact = "Lower values duty-cycle mobs closer to players for more savings at the cost of visible AI hitches nearby.")
  private double dutyCycleStartDistance = 24;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Number of rotating awareness slots; each duty-cycled mob is aware for one slot per rotation.", impact = "Higher values keep fewer mobs aware at once (more savings, slower mob reactions); lower values are gentler.")
  private int dutyCycleSlots = 4;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before duty-cycling engages.", impact = "Lower values engage AI shedding earlier; higher values reserve it for heavier load.")
  private double dutyCycleMinTickMs = 42;
  private transient boolean dutyCycleSupported;
  private transient volatile int dutyCycleIndex;
  private transient volatile double lastTickMs;
  private transient volatile boolean active;
  private transient volatile WorldHost worlds;
  private transient volatile EntityHost entities;
  private transient volatile EntityActivityHost activity;
  private transient volatile Subscription wakeHooks = Subscription.NONE;

  public FeatureAdaptiveEntitySleep() {
    super(ID);
  }

  @Override
  public void onActivate() {
    lifecycleLock.writeLock().lock();
    try {
      active = false;
      lifecycleGeneration.incrementAndGet();
      sleepScanGeneration.set(0L);
      foliaScanFlight.set(null);
      nextFoliaAnchor.set(0);
      nextPaperWorld.set(0);
      dutyCycleSupported = false;
      dutyCycleIndex = 0;
      lastTickMs = 0;
      EntityActivityHost host = activity();
      if (host != null && host.supportsAwareness()) {
        dutyCycleSupported = true;
      } else {
        verbose("Adaptive entity sleep duty-cycling unavailable: Mob#setAware not present on this server software.");
      }
      wakeHooks.cancel();
      wakeHooks = host == null ? Subscription.NONE : subscribeWakeHooks(host);
      active = true;
    } finally {
      lifecycleLock.writeLock().unlock();
    }
  }

  @Override
  public void onDeactivate() {
    lifecycleLock.writeLock().lock();
    try {
      active = false;
      lifecycleGeneration.incrementAndGet();
      sleepScanGeneration.set(0L);
      Subscription hooks = wakeHooks;
      wakeHooks = Subscription.NONE;
      hooks.cancel();
      FoliaScanFlight flight = foliaScanFlight.getAndSet(null);
      if (flight != null) {
        flight.cancel();
      }
      ReactEntity.releasePauseOwner(ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
      ReactEntity.releaseAllDozes();
    } finally {
      lifecycleLock.writeLock().unlock();
    }
  }

  @Override
  public int getTickInterval() {
    return Math.max(50, tickIntervalMS);
  }

  @Override
  public void onTick() {
    long generation = lifecycleGeneration.get();
    if (!isActive(generation)) {
      return;
    }

    double sampledTickMs = sample(SamplerTickTime.ID);
    if (!isActive(generation)) {
      return;
    }
    dutyCycleIndex++;
    lastTickMs = sampledTickMs;

    if (J.isFoliaThreading()) {
      FoliaScanFlight flight = new FoliaScanFlight(generation);
      if (foliaScanFlight.compareAndSet(null, flight)) {
        applyFoliaSleepScan(flight);
      }
      return;
    }

    if (!sleepScanGeneration.compareAndSet(0L, generation)) {
      return;
    }

    JobBatch batch = new JobBatch(JobBatch.scanLimits(), () -> sleepScanGeneration.compareAndSet(generation, 0L));
    J.s(() -> {
      try {
        if (isActive(generation)) {
          queueSleepScan(generation, batch);
        }
      } finally {
        batch.seal();
      }
    });
  }

  private Subscription subscribeWakeHooks(EntityActivityHost host) {
    Subscription damage = host.onDamage(this::onDamage);
    Subscription target = host.onTarget(this::onTarget);
    return () -> {
      damage.cancel();
      target.cancel();
    };
  }

  private void queueSleepScan(long generation, JobBatch batch) {
    if (!isActive(generation)) {
      return;
    }

    WorldHost worldHost = worlds();
    EntityActivityHost host = activity();
    List<ReactWorld> loaded = worldHost == null ? List.of() : worldHost.worlds();
    if (loaded.isEmpty() || host == null) {
      return;
    }
    int budget = Math.max(1, maxEntitiesSampledPerCycle);
    int start = Math.floorMod(nextPaperWorld.getAndIncrement(), loaded.size());
    for (int index = 0; index < loaded.size(); index++) {
      ReactWorld world = loaded.get((start + index) % loaded.size());
      if (budget <= 0) {
        return;
      }

      List<GameEntity> sampled = host.nextEntities(world, budget);
      if (sampled.isEmpty()) {
        continue;
      }

      for (GameEntity entity : sampled) {
        if (!isActive(generation)) {
          return;
        }
        batch.submit(() -> manageEntity(entity, generation));
        budget--;

        if (budget <= 0) {
          return;
        }
      }
    }
  }

  private void applyFoliaSleepScan(FoliaScanFlight flight) {
    long generation = flight.generation;
    if (!isActive(generation) || !flight.isCurrent(lifecycleGeneration.get())) {
      finishFoliaScheduling(flight);
      return;
    }

    WorldHost worldHost = worlds();
    List<ReactPlayer> players = worldHost == null ? null : worldHost.onlinePlayers();
    if (!isActive(generation) || players == null || players.isEmpty()) {
      finishFoliaScheduling(flight);
      return;
    }

    int budget = Math.max(1, maxEntitiesSampledPerCycle);
    AtomicInteger remaining = new AtomicInteger(budget);
    int playerSamples = Math.min(players.size(), Math.max(1, (budget + 7) / 8));
    int perAnchor = Math.max(1, (budget + playerSamples - 1) / playerSamples);
    int start = Math.floorMod(nextFoliaAnchor.getAndAdd(playerSamples), players.size());

    for (int i = 0; i < playerSamples && remaining.get() > 0; i++) {
      if (!isActive(generation) || !flight.isCurrent(lifecycleGeneration.get())) {
        break;
      }
      ReactPlayer player = players.get((start + i) % players.size());
      scheduleFoliaTask(
          flight,
          player.handle(),
          () -> runFoliaAnchor(player, remaining, perAnchor, flight)
      );
    }
    finishFoliaScheduling(flight);
  }

  private void runFoliaAnchor(
      ReactPlayer player,
      AtomicInteger remaining,
      int perAnchor,
      FoliaScanFlight flight
  ) {
    lifecycleLock.readLock().lock();
    try {
      if (isActive(flight.generation) && flight.isCurrent(lifecycleGeneration.get())) {
        sampleAroundPlayer(player, remaining, perAnchor, flight);
      }
    } finally {
      lifecycleLock.readLock().unlock();
    }
  }

  private void sampleAroundPlayer(
      ReactPlayer player,
      AtomicInteger remaining,
      int perAnchor,
      FoliaScanFlight flight
  ) {
    long generation = flight.generation;
    WorldHost worldHost = worlds();
    if (!isActive(generation)
        || !flight.isCurrent(lifecycleGeneration.get())
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
        player,
        ID,
        sleepBeyondNearestPlayer + 16,
        Math.max(32, sleepBeyondNearestPlayer),
        perAnchor,
        entity -> isActive(generation) && flight.isCurrent(lifecycleGeneration.get()) && remaining.getAndDecrement() > 0
    );
    if (nearby.isEmpty()) {
      return;
    }

    int samples = Math.min(nearby.size(), perAnchor);
    for (int i = 0; i < samples; i++) {
      if (!isActive(generation) || !flight.isCurrent(lifecycleGeneration.get())) {
        return;
      }

      GameEntity entity = nearby.get(i);
      manageEntity(entity, generation, flight);
    }
  }

  private void manageEntity(GameEntity entity, long generation) {
    manageEntity(entity, generation, null);
  }

  private void manageEntity(GameEntity entity, long generation, FoliaScanFlight flight) {
    if (!isActive(generation) || entity == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity.handle())) {
      if (flight == null) {
        J.runEntity(entity.handle(), () -> manageEntity(entity, generation));
      } else {
        scheduleFoliaTask(flight, entity.handle(), () -> manageEntity(entity, generation, flight));
      }
      return;
    }

    lifecycleLock.readLock().lock();
    try {
      if (!isActive(generation)) {
        return;
      }
      manageOwnedEntity(entity);
    } finally {
      lifecycleLock.readLock().unlock();
    }
  }

  private void scheduleFoliaTask(FoliaScanFlight flight, Object owner, Runnable operation) {
    if (owner == null || !flight.tryRegisterTask()) {
      return;
    }

    AtomicBoolean completionClaimed = new AtomicBoolean(false);
    Runnable completed = () -> {
      if (completionClaimed.compareAndSet(false, true) && flight.completeTask()) {
        foliaScanFlight.compareAndSet(flight, null);
      }
    };
    try {
      boolean scheduled = J.runEntity(
          owner,
          () -> {
            try {
              if (isActive(flight.generation) && flight.isCurrent(lifecycleGeneration.get())) {
                operation.run();
              }
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
    } catch (Throwable failure) {
      completed.run();
      reportError(failure);
    }
  }

  private void finishFoliaScheduling(FoliaScanFlight flight) {
    if (flight.seal()) {
      foliaScanFlight.compareAndSet(flight, null);
    }
  }

  private void manageOwnedEntity(GameEntity entity) {
    if (!canManage(entity)) {
      wakeOwned(entity);
      return;
    }

    WorldHost worldHost = worlds();
    GameLocation location = entity.location();
    if (worldHost != null && worldHost.anyPlayerNear(location, dutyCycleStartDistance)) {
      wakeOwned(entity);
      return;
    }

    if (worldHost != null && worldHost.anyPlayerNear(location, sleepBeyondNearestPlayer)) {
      ReactEntity.releasePause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
      applyDutyCycleOwned(entity);
      return;
    }

    ReactEntity.requestPause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
  }

  private void applyDutyCycleOwned(GameEntity entity) {
    if (!dutyCycleSupported || !dutyCycleEnabled || !entity.isMob()) {
      return;
    }

    double effectiveTickMs = lastTickMs;
    GameLocation location = entity.location();
    if (location != null && PerWorldPressure.get(location.worldId()).isPressure()) {
      effectiveTickMs = Math.max(effectiveTickMs, dutyCycleMinTickMs);
    }

    if (shouldDoze(effectiveTickMs, dutyCycleMinTickMs, dutyCycleSlots, activity().entityId(entity), dutyCycleIndex)) {
      dozeOwned(entity);
    } else {
      undozeOwned(entity);
    }
  }

  private void dozeOwned(GameEntity mob) {
    if (ReactEntity.isDozing(mob)) {
      return;
    }

    EntityHost host = entities();
    if (host == null || !host.isAware(mob)) {
      return;
    }

    ReactEntity.requestDoze(mob);
  }

  private void undozeOwned(GameEntity mob) {
    if (!dutyCycleSupported || !ReactEntity.isDozing(mob)) {
      return;
    }

    ReactEntity.releaseDoze(mob);
  }

  private boolean canManage(GameEntity entity) {
    if (!entity.isLiving()) {
      return false;
    }

    EntityHost host = entities();
    EntityActivityHost traits = activity();
    if (host == null || traits == null || host.isProtected(entity, ReactOperation.SLEEP)) {
      return false;
    }

    if (entity.isPlayer() || !entity.isValid()) {
      return false;
    }

    if (entity.ticksLived() < minimumEntityAgeTicks) {
      return false;
    }

    if (ignoreNamedEntities && traits.isNamed(entity)) {
      return false;
    }

    if (ignoreTamedEntities && entity.isTamed()) {
      return false;
    }

    if (ignoreVillagers && traits.isVillager(entity)) {
      return false;
    }

    if (ignoreBosses && traits.isBoss(entity)) {
      return false;
    }

    return !ignorePersistentEntities || !traits.isPersistent(entity);
  }

  private void wake(GameEntity entity, long generation) {
    if (!isActive(generation)) {
      return;
    }

    lifecycleLock.readLock().lock();
    try {
      if (isActive(generation)) {
        wakeOwned(entity);
      }
    } finally {
      lifecycleLock.readLock().unlock();
    }
  }

  private void wakeOnOwner(GameEntity entity, long generation) {
    if (!isActive(generation) || entity == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity.handle())) {
      J.runEntity(entity.handle(), () -> wake(entity, generation));
      return;
    }

    wake(entity, generation);
  }

  private void wakeOwned(GameEntity entity) {
    if (entity == null || !entity.isValid()) {
      return;
    }

    ReactEntity.releasePause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);

    if (entity.isMob()) {
      undozeOwned(entity);
    }
  }

  private boolean isActive(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  void onDamage(GameEntity entity) {
    long generation = lifecycleGeneration.get();
    if (wakeOnDamage && isActive(generation)) {
      wakeIfManaged(entity, generation);
    }
  }

  void onTarget(GameEntity entity, GameEntity target) {
    long generation = lifecycleGeneration.get();
    if (!wakeOnTarget || !isActive(generation)) {
      return;
    }

    wakeIfManaged(entity, generation);
    wakeIfManaged(target, generation);
  }

  private void wakeIfManaged(GameEntity entity, long generation) {
    if (holdsSleepState(entity)) {
      wakeOnOwner(entity, generation);
    }
  }

  private boolean holdsSleepState(GameEntity entity) {
    if (entity == null) {
      return false;
    }
    if (ReactEntity.isPausedBy(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP)) {
      return true;
    }
    EntityHost host = entities();
    return dutyCycleSupported && entity.isMob() && host != null && !host.isAware(entity);
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

  private static void verbose(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.verbose(message);
    }
  }

  private static void reportError(Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(failure);
    }
  }
  private static final class FoliaScanFlight {
    private final long generation;
    private boolean canceled;
    private boolean sealed;
    private int pendingTasks = 1;

    private FoliaScanFlight(long generation) {
      this.generation = generation;
    }

    private synchronized boolean tryRegisterTask() {
      if (canceled || pendingTasks == 0) {
        return false;
      }
      pendingTasks++;
      return true;
    }

    private synchronized boolean isCurrent(long currentGeneration) {
      return !canceled && generation == currentGeneration;
    }

    private synchronized boolean completeTask() {
      pendingTasks--;
      return sealed && pendingTasks == 0;
    }

    private synchronized boolean seal() {
      sealed = true;
      pendingTasks--;
      return pendingTasks == 0;
    }

    private synchronized void cancel() {
      canceled = true;
    }
  }
}
