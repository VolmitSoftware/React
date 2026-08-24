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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Adaptive Entity Sleep feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureAdaptiveEntitySleep extends ReactFeature implements Listener {
  static boolean shouldDoze(double lastTickMs, double dutyCycleMinTickMs, int dutyCycleSlots, int entityId, int dutyCycleIndex) {
    if (lastTickMs < dutyCycleMinTickMs) {
      return false;
    }

    return Math.floorMod(entityId + dutyCycleIndex, Math.max(2, dutyCycleSlots)) != 0;
  }

  public static final String ID = "adaptive-entity-sleep";
  private transient final AtomicLong sleepScanGeneration = new AtomicLong(0L);
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
      dutyCycleSupported = false;
      dutyCycleIndex = 0;
      lastTickMs = 0;
      try {
        Mob.class.getMethod("setAware", boolean.class);
        Mob.class.getMethod("isAware");
        dutyCycleSupported = true;
      } catch (NoSuchMethodException e) {
        React.verbose("Adaptive entity sleep duty-cycling unavailable: Mob#setAware not present on this server software.");
      }
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

    double sampledTickMs = sampleTickMs();
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

    J.s(() -> {
      try {
        if (isActive(generation)) {
          applySleepScan(generation);
        }
      } finally {
        sleepScanGeneration.compareAndSet(generation, 0L);
      }
    });
  }

  private double sampleTickMs() {
    try {
      art.arcane.react.api.sampler.Sampler sampler = React.sampler(SamplerTickTime.ID);
      return sampler == null ? 0D : sampler.sample();
    } catch (Throwable ignored) {
      return 0D;
    }
  }

  private void applySleepScan(long generation) {
    if (!isActive(generation)) {
      return;
    }

    int budget = Math.max(1, maxEntitiesSampledPerCycle);
    for (World world : Bukkit.getWorlds()) {
      if (budget <= 0) {
        return;
      }

      List<Entity> entities = WorldEntitySnapshots.next(world, budget);
      if (entities.isEmpty()) {
        continue;
      }

      for (Entity entity : entities) {
        if (!isActive(generation)) {
          return;
        }
        manageEntity(entity, generation);
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

    EntityController controller = React.controller(EntityController.class);
    Player[] players = controller == null ? null : controller.getFoliaPlayers();
    if (!isActive(generation) || players == null || players.length == 0) {
      finishFoliaScheduling(flight);
      return;
    }

    int budget = Math.max(1, maxEntitiesSampledPerCycle);
    AtomicInteger remaining = new AtomicInteger(budget);
    int playerSamples = Math.min(players.length, Math.max(1, (budget + 7) / 8));
    int perAnchor = Math.max(1, (budget + playerSamples - 1) / playerSamples);
    int start = Math.floorMod(nextFoliaAnchor.getAndAdd(playerSamples), players.length);

    for (int i = 0; i < playerSamples && remaining.get() > 0; i++) {
      if (!isActive(generation) || !flight.isCurrent(lifecycleGeneration.get())) {
        break;
      }
      Player player = players[(start + i) % players.length];
      scheduleFoliaTask(
          flight,
          player,
          () -> runFoliaAnchor(player, remaining, perAnchor, flight)
      );
    }
    finishFoliaScheduling(flight);
  }

  private void runFoliaAnchor(
      Player player,
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
      Player player,
      AtomicInteger remaining,
      int perAnchor,
      FoliaScanFlight flight
  ) {
    long generation = flight.generation;
    if (!isActive(generation)
        || !flight.isCurrent(lifecycleGeneration.get())
        || player == null
        || !player.isOnline()
        || !J.isOwnedByCurrentRegion(player)) {
      return;
    }

    List<Entity> nearby = player.getNearbyEntities(
        sleepBeyondNearestPlayer + 16,
        Math.max(32, sleepBeyondNearestPlayer),
        sleepBeyondNearestPlayer + 16
    );
    if (nearby.isEmpty()) {
      return;
    }

    int samples = Math.min(nearby.size(), perAnchor);
    int start = ThreadLocalRandom.current().nextInt(nearby.size());
    for (int i = 0; i < samples; i++) {
      if (!isActive(generation)
          || !flight.isCurrent(lifecycleGeneration.get())
          || remaining.getAndDecrement() <= 0) {
        return;
      }

      Entity entity = nearby.get((start + i) % nearby.size());
      manageEntity(entity, generation, flight);
    }
  }

  private void manageEntity(Entity entity, long generation) {
    manageEntity(entity, generation, null);
  }

  private void manageEntity(Entity entity, long generation, FoliaScanFlight flight) {
    if (!isActive(generation) || entity == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity)) {
      if (flight == null) {
        J.runEntity(entity, () -> manageEntity(entity, generation));
      } else {
        scheduleFoliaTask(flight, entity, () -> manageEntity(entity, generation, flight));
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

  private void scheduleFoliaTask(FoliaScanFlight flight, Entity entity, Runnable operation) {
    if (entity == null || !flight.tryRegisterTask()) {
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
          entity,
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
      React.reportError(failure);
    }
  }

  private void finishFoliaScheduling(FoliaScanFlight flight) {
    if (flight.seal()) {
      foliaScanFlight.compareAndSet(flight, null);
    }
  }

  private void manageOwnedEntity(Entity entity) {
    if (!canManage(entity)) {
      wakeOwned(entity);
      return;
    }

    Location location = entity.getLocation();
    if (React.hasNearbyPlayer(location, dutyCycleStartDistance)) {
      wakeOwned(entity);
      return;
    }

    if (React.hasNearbyPlayer(location, sleepBeyondNearestPlayer)) {
      ReactEntity.releasePause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
      applyDutyCycleOwned(entity);
      return;
    }

    ReactEntity.requestPause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
  }

  private void applyDutyCycleOwned(Entity entity) {
    if (!dutyCycleSupported || !dutyCycleEnabled || !(entity instanceof Mob mob)) {
      return;
    }

    double effectiveTickMs = lastTickMs;
    if (PerWorldPressure.get(entity.getWorld()).isPressure()) {
      effectiveTickMs = Math.max(effectiveTickMs, dutyCycleMinTickMs);
    }

    if (shouldDoze(effectiveTickMs, dutyCycleMinTickMs, dutyCycleSlots, mob.getEntityId(), dutyCycleIndex)) {
      dozeOwned(mob);
    } else {
      undozeOwned(mob);
    }
  }

  private void dozeOwned(Mob mob) {
    if (ReactEntity.isDozing(mob)) {
      return;
    }

    if (!mob.isAware()) {
      return;
    }

    ReactEntity.requestDoze(mob);
  }

  private void undozeOwned(Mob mob) {
    if (!dutyCycleSupported || !ReactEntity.isDozing(mob)) {
      return;
    }

    ReactEntity.releaseDoze(mob);
  }

  private boolean canManage(Entity entity) {
    if (!(entity instanceof LivingEntity living)) {
      return false;
    }

    if (ReactProtection.isProtected(entity, ReactOperation.SLEEP)) {
      return false;
    }

    if (entity instanceof Player || entity.isDead()) {
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

    if (ignoreTamedEntities && living instanceof Tameable tameable && tameable.isTamed()) {
      return false;
    }

    if (ignoreVillagers && living instanceof Villager) {
      return false;
    }

    if (ignoreBosses && isBoss(living)) {
      return false;
    }

    return !ignorePersistentEntities || !living.isPersistent();
  }

  private boolean isBoss(LivingEntity entity) {
    return entity instanceof EnderDragon || entity instanceof Wither || entity instanceof Warden;
  }

  private void wake(Entity entity, long generation) {
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

  private void wakeOnOwner(Entity entity, long generation) {
    if (!isActive(generation) || entity == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity)) {
      J.runEntity(entity, () -> wake(entity, generation));
      return;
    }

    wake(entity, generation);
  }

  private void wakeOwned(Entity entity) {
    if (entity == null || entity.isDead()) {
      return;
    }

    ReactEntity.releasePause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);

    if (entity instanceof Mob mob) {
      undozeOwned(mob);
    }
  }

  private boolean isActive(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  @EventHandler(ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    long generation = lifecycleGeneration.get();
    if (wakeOnDamage && isActive(generation)) {
      wakeOnOwner(event.getEntity(), generation);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(EntityTargetEvent event) {
    long generation = lifecycleGeneration.get();
    if (!wakeOnTarget || !isActive(generation)) {
      return;
    }

    wakeOnOwner(event.getEntity(), generation);
    wakeOnOwner(event.getTarget(), generation);
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
