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
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Dynamic Activation Range feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureDynamicActivationRange extends ReactFeature implements Listener {
  public static final String ID = "dynamic-activation-range";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for dynamic activation range in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entities sampled allowed per cycle in dynamic activation range.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxEntitiesSampledPerCycle = 240;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum activation range required by dynamic activation range.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private double minimumActivationRange = 18;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum activation range allowed by dynamic activation range.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maximumActivationRange = 64;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Activation radius used by dynamic activation range (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double currentActivationRange = maximumActivationRange;
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
    currentActivationRange = maximumActivationRange;
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
    double tickTime = React.sampler(SamplerTickTime.ID).sample();
    tuneRange(tickTime);

    if (J.isFoliaThreading()) {
      applyFoliaActivationRange();
      return;
    }

    J.s(this::applyActivationRange);
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

  private void applyActivationRange() {
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

      int sample = Math.min(budget, entities.size());
      for (int i = 0; i < sample; i++) {
        Entity entity = entities.get(random.nextInt(entities.size()));
        budget--;
        manage(entity);
        if (budget <= 0) {
          return;
        }
      }
    }
  }

  private void applyFoliaActivationRange() {
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
        currentActivationRange + 16,
        Math.max(32, currentActivationRange),
        currentActivationRange + 16
    );
    if (nearby.isEmpty()) {
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    int sample = Math.min(nearby.size(), Math.max(1, remaining.get() / 2));
    for (int i = 0; i < sample; i++) {
      if (remaining.getAndDecrement() <= 0) {
        return;
      }

      Entity entity = nearby.get(random.nextInt(nearby.size()));
      manage(entity);
    }
  }

  private void manage(Entity entity) {
    if (entity == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity)) {
      J.runEntity(entity, () -> manage(entity));
      return;
    }

    if (!canManage(entity)) {
      wake(entity);
      return;
    }

    if (React.hasNearbyPlayer(entity.getLocation(), currentActivationRange)) {
      wake(entity);
      return;
    }

    if (!ReactEntity.isPaused(entity)) {
      ReactEntity.setPaused(entity, true);
    }
  }

  private boolean canManage(Entity entity) {
    if (!(entity instanceof LivingEntity living) || entity.isDead() || entity instanceof Player) {
      return false;
    }

    if (entity.getTicksLived() < minimumEntityAgeTicks) {
      return false;
    }

    if (ignoreNamedEntities && living.getCustomName() != null && !living.getCustomName().isBlank()) {
      return false;
    }

    return !(ignoreTamedEntities && living instanceof Tameable tameable && tameable.isTamed());
  }

  private void wake(Entity entity) {
    if (entity != null && !entity.isDead() && ReactEntity.isPaused(entity)) {
      ReactEntity.setPaused(entity, false);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    wake(event.getEntity());
  }

  @EventHandler(ignoreCancelled = true)
  public void on(EntityTargetEvent event) {
    wake(event.getEntity());
    wake(event.getTarget());
  }
}
