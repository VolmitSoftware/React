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
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Adaptive Entity Sleep feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureAdaptiveEntitySleep extends ReactFeature implements Listener {
  public static final String ID = "adaptive-entity-sleep";
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

  public FeatureAdaptiveEntitySleep() {
    super(ID);
  }

  @Override
  public void onActivate() {

  }

  @Override
  public void onDeactivate() {

  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    if (J.isFoliaThreading()) {
      applyFoliaSleepScan();
      return;
    }

    J.s(this::applySleepScan);
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

    if (React.hasNearbyPlayer(entity.getLocation(), sleepBeyondNearestPlayer)) {
      wake(entity);
      return;
    }

    if (!ReactEntity.isPaused(entity)) {
      ReactEntity.setPaused(entity, true);
    }
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
    if (entity != null && !entity.isDead() && ReactEntity.isPaused(entity)) {
      ReactEntity.setPaused(entity, false);
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
