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
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Adaptive Entity Sleep feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureAdaptiveEntitySleep extends ReactFeature implements Listener {
  static boolean shouldDoze(double lastTickMs, double dutyCycleMinTickMs, int dutyCycleSlots, int entityId, int dutyCycleIndex) {
    if (lastTickMs < dutyCycleMinTickMs) {
      return false;
    }

    return Math.floorMod(entityId + dutyCycleIndex, Math.max(2, dutyCycleSlots)) != 0;
  }

  public static final String ID = "adaptive-entity-sleep";
  private static final NamespacedKey nsDozing = new NamespacedKey(React.instance, "react-dozing");
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
  private transient int dutyCycleIndex;
  private transient double lastTickMs;

  public FeatureAdaptiveEntitySleep() {
    super(ID);
  }

  @Override
  public void onActivate() {
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
  }

  @Override
  public void onDeactivate() {
    if (J.isFoliaThreading()) {
      return;
    }

    J.s(() -> {
      for (World world : Bukkit.getWorlds()) {
        for (Entity entity : world.getEntities()) {
          if (entity instanceof Mob mob) {
            undoze(mob);
          }
        }
      }
    });
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    dutyCycleIndex++;
    lastTickMs = sampleTickMs();

    if (J.isFoliaThreading()) {
      applyFoliaSleepScan();
      return;
    }

    J.s(this::applySleepScan);
  }

  private double sampleTickMs() {
    try {
      art.arcane.react.api.sampler.Sampler sampler = React.sampler(SamplerTickTime.ID);
      return sampler == null ? 0D : sampler.sample();
    } catch (Throwable ignored) {
      return 0D;
    }
  }

  private void applySleepScan() {
    int budget = Math.max(1, maxEntitiesSampledPerCycle);
    ThreadLocalRandom random = ThreadLocalRandom.current();

    for (World world : Bukkit.getWorlds()) {
      if (budget <= 0) {
        return;
      }

      List<Entity> entities = world.getEntities();
      if (entities.isEmpty()) {
        continue;
      }

      int sampleCount = Math.min(entities.size(), budget);
      for (int i = 0; i < sampleCount; i++) {
        Entity entity = entities.get(random.nextInt(entities.size()));
        manageEntity(entity);
        budget--;

        if (budget <= 0) {
          return;
        }
      }
    }
  }

  private void applyFoliaSleepScan() {
    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (players.isEmpty()) {
      return;
    }

    AtomicInteger remaining = new AtomicInteger(Math.max(1, maxEntitiesSampledPerCycle));
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int playerSamples = Math.min(players.size(), Math.max(1, remaining.get() / 8));

    for (int i = 0; i < playerSamples && remaining.get() > 0; i++) {
      Player player = players.get(random.nextInt(players.size()));
      J.runEntity(player, () -> sampleAroundPlayer(player, remaining));
    }
  }

  private void sampleAroundPlayer(Player player, AtomicInteger remaining) {
    if (player == null || !player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
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

    ThreadLocalRandom random = ThreadLocalRandom.current();
    int samples = Math.min(nearby.size(), Math.max(1, remaining.get() / 2));
    for (int i = 0; i < samples; i++) {
      if (remaining.getAndDecrement() <= 0) {
        return;
      }

      Entity entity = nearby.get(random.nextInt(nearby.size()));
      manageEntity(entity);
    }
  }

  private void manageEntity(Entity entity) {
    if (entity == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity)) {
      J.runEntity(entity, () -> manageEntity(entity));
      return;
    }

    if (!canManage(entity)) {
      wake(entity);
      return;
    }

    Location location = entity.getLocation();
    if (React.hasNearbyPlayer(location, dutyCycleStartDistance)) {
      wake(entity);
      return;
    }

    if (React.hasNearbyPlayer(location, sleepBeyondNearestPlayer)) {
      if (ReactEntity.isPaused(entity)) {
        ReactEntity.setPaused(entity, false);
      }
      applyDutyCycle(entity);
      return;
    }

    if (!ReactEntity.isPaused(entity)) {
      ReactEntity.setPaused(entity, true);
    }
  }

  private void applyDutyCycle(Entity entity) {
    if (!dutyCycleSupported || !dutyCycleEnabled || !(entity instanceof Mob mob)) {
      return;
    }

    if (shouldDoze(lastTickMs, dutyCycleMinTickMs, dutyCycleSlots, mob.getEntityId(), dutyCycleIndex)) {
      doze(mob);
    } else {
      undoze(mob);
    }
  }

  private void doze(Mob mob) {
    if (isDozing(mob)) {
      return;
    }

    // A mob that is already unaware without our flag was set by vanilla or another
    // plugin; leave external awareness state alone.
    if (!mob.isAware()) {
      return;
    }

    mob.getPersistentDataContainer().set(nsDozing, PersistentDataType.BYTE, (byte) 1);
    mob.setAware(false);
  }

  private void undoze(Mob mob) {
    if (!dutyCycleSupported || !isDozing(mob)) {
      return;
    }

    mob.setAware(true);
    mob.getPersistentDataContainer().remove(nsDozing);
  }

  private boolean isDozing(Mob mob) {
    return mob.getPersistentDataContainer().getOrDefault(nsDozing, PersistentDataType.BYTE, (byte) 0) == 1;
  }

  private boolean canManage(Entity entity) {
    if (!(entity instanceof LivingEntity living)) {
      return false;
    }

    if (entity instanceof Player || entity.isDead()) {
      return false;
    }

    if (entity.getTicksLived() < minimumEntityAgeTicks) {
      return false;
    }

    if (ignoreNamedEntities && living.getCustomName() != null && !living.getCustomName().isBlank()) {
      return false;
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

  private void wake(Entity entity) {
    if (entity == null || entity.isDead()) {
      return;
    }

    if (ReactEntity.isPaused(entity)) {
      ReactEntity.setPaused(entity, false);
    }

    if (entity instanceof Mob mob) {
      undoze(mob);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    if (wakeOnDamage) {
      wake(event.getEntity());
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(EntityTargetEvent event) {
    if (!wakeOnTarget) {
      return;
    }

    wake(event.getEntity());
    wake(event.getTarget());
  }
}
