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
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.bridge.BridgeKind;
import art.arcane.react.core.bridge.NmsBridgeDescriptor;
import art.arcane.react.core.bridge.NmsBridgeHandle;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Pathfinder Budget feature. Shrinks the A* node budget of mobs away from players while the server is under load, making every path search cheaper without changing where mobs try to go. Budgets are restored when mobs come near players, when the server recovers, or on deactivation. Fails closed to vanilla pathfinding if the NMS navigation bridges are unavailable.")
public class FeaturePathfinderBudget extends ReactFeature {
  public static final String ID = "pathfinder-budget";
  public static final String BRIDGE_GET_NAVIGATION = "Mob.getNavigation";
  public static final String BRIDGE_SET_MAX_VISITED = "PathNavigation.setMaxVisitedNodesMultiplier";
  public static final String BRIDGE_RESET_MAX_VISITED = "PathNavigation.resetMaxVisitedNodesMultiplier";
  private static final NamespacedKey nsBudgeted = new NamespacedKey(React.instance, "react-path-budget");
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for pathfinder budget in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum mobs sampled per cycle.", impact = "Higher values converge on the mob population faster at more per-cycle cost.")
  private int maxEntitiesSampledPerCycle = 240;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before pathfinding budgets shrink.", impact = "Lower values shed pathfinding cost earlier; higher values reserve it for heavier load.")
  private double engageTickTimeMs = 48;
  @art.arcane.react.util.project.config.ConfigDoc(value = "A* visited-node budget multiplier applied to distant mobs while engaged (vanilla is 1.0).", impact = "Lower values make path searches cheaper but produce worse long-distance paths for affected mobs.")
  private double budgetMultiplier = 0.4;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Mobs within this distance of a player always keep their full pathfinding budget (blocks).", impact = "Higher values protect more mobs from budgeting; lower values shed more pathfinding cost near players.")
  private double fullBudgetWithinDistance = 16;
  private transient NmsBridgeHandle bridgeGetNavigation;
  private transient NmsBridgeHandle bridgeSetMaxVisited;
  private transient NmsBridgeHandle bridgeResetMaxVisited;
  private transient boolean bridgesAvailable;
  private transient double lastTickMs;

  public FeaturePathfinderBudget() {
    super(ID);
  }

  public static List<NmsBridgeDescriptor> pathfinderBridgeDescriptors() {
    List<String> mobClasses = List.of(
        "net.minecraft.world.entity.Mob",
        "net.minecraft.world.entity.EntityInsentient");
    List<String> navigationClasses = List.of(
        "net.minecraft.world.entity.ai.navigation.PathNavigation",
        "net.minecraft.world.entity.ai.navigation.NavigationAbstract");
    return List.of(
        new NmsBridgeDescriptor(
            BRIDGE_GET_NAVIGATION, BridgeKind.METHOD, mobClasses, "getNavigation",
            List.of(List.of()),
            navigationClasses.get(0),
            Optional.of("Mob.getNavigation")),
        new NmsBridgeDescriptor(
            BRIDGE_SET_MAX_VISITED, BridgeKind.METHOD, navigationClasses, "setMaxVisitedNodesMultiplier",
            List.of(List.of("float")),
            "void",
            Optional.of("PathNavigation.setMaxVisitedNodesMultiplier")),
        new NmsBridgeDescriptor(
            BRIDGE_RESET_MAX_VISITED, BridgeKind.METHOD, navigationClasses, "resetMaxVisitedNodesMultiplier",
            List.of(List.of()),
            "void",
            Optional.of("PathNavigation.resetMaxVisitedNodesMultiplier"))
    );
  }

  @Override
  public void onActivate() {
    lastTickMs = 0;
    List<NmsBridgeDescriptor> descriptors = pathfinderBridgeDescriptors();
    bridgeGetNavigation = React.bridgeRegistry().resolve(descriptors.get(0));
    bridgeSetMaxVisited = React.bridgeRegistry().resolve(descriptors.get(1));
    bridgeResetMaxVisited = React.bridgeRegistry().resolve(descriptors.get(2));
    bridgesAvailable = bridgeGetNavigation.available()
        && bridgeSetMaxVisited.available()
        && bridgeResetMaxVisited.available();
    if (!bridgesAvailable) {
      setEnabled(false);
      React.warn("Pathfinder Budget disabled: NMS navigation bridges unavailable on this server software.");
    }
  }

  @Override
  public void onDeactivate() {
    if (!bridgesAvailable || J.isFoliaThreading()) {
      return;
    }

    J.s(() -> {
      for (World world : Bukkit.getWorlds()) {
        for (Entity entity : world.getEntities()) {
          if (entity instanceof Mob mob && isBudgeted(mob)) {
            restoreBudget(mob);
          }
        }
      }
    });
  }

  @Override
  public int getTickInterval() {
    return Math.max(250, tickIntervalMS);
  }

  @Override
  public void onTick() {
    if (!bridgesAvailable) {
      return;
    }

    lastTickMs = sampleTickMs();
    if (J.isFoliaThreading()) {
      applyFoliaScan();
      return;
    }

    J.s(this::applyScan);
  }

  private void applyScan() {
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
        manageEntity(entities.get(random.nextInt(entities.size())));
        budget--;

        if (budget <= 0) {
          return;
        }
      }
    }
  }

  private void applyFoliaScan() {
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

    double range = Math.max(32, fullBudgetWithinDistance * 4);
    List<Entity> nearby = player.getNearbyEntities(range, Math.max(32, range / 2), range);
    if (nearby.isEmpty()) {
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    int samples = Math.min(nearby.size(), Math.max(1, remaining.get() / 2));
    for (int i = 0; i < samples; i++) {
      if (remaining.getAndDecrement() <= 0) {
        return;
      }

      manageEntity(nearby.get(random.nextInt(nearby.size())));
    }
  }

  private void manageEntity(Entity entity) {
    if (!(entity instanceof Mob mob) || entity.isDead()) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity)) {
      J.runEntity(entity, () -> manageEntity(entity));
      return;
    }

    boolean engagedLoad = lastTickMs >= engageTickTimeMs;
    if (!engagedLoad || React.hasNearbyPlayer(mob.getLocation(), fullBudgetWithinDistance)) {
      if (isBudgeted(mob)) {
        restoreBudget(mob);
      }
      return;
    }

    if (!isBudgeted(mob)) {
      applyBudget(mob);
    }
  }

  private void applyBudget(Mob mob) {
    try {
      Object navigation = navigationOf(mob);
      if (navigation == null) {
        return;
      }

      bridgeSetMaxVisited.methodHandle().invokeWithArguments(navigation, (float) Math.max(0.05D, Math.min(1D, budgetMultiplier)));
      mob.getPersistentDataContainer().set(nsBudgeted, PersistentDataType.BYTE, (byte) 1);
    } catch (Throwable e) {
      failRuntime(e);
    }
  }

  private void restoreBudget(Mob mob) {
    try {
      Object navigation = navigationOf(mob);
      if (navigation == null) {
        return;
      }

      bridgeResetMaxVisited.methodHandle().invokeWithArguments(navigation);
      mob.getPersistentDataContainer().remove(nsBudgeted);
    } catch (Throwable e) {
      failRuntime(e);
    }
  }

  private Object navigationOf(Mob mob) throws Throwable {
    Object handle = mob.getClass().getMethod("getHandle").invoke(mob);
    if (handle == null) {
      return null;
    }

    return bridgeGetNavigation.methodHandle().invokeWithArguments(handle);
  }

  private boolean isBudgeted(Mob mob) {
    return mob.getPersistentDataContainer().getOrDefault(nsBudgeted, PersistentDataType.BYTE, (byte) 0) == 1;
  }

  private void failRuntime(Throwable e) {
    if (!bridgesAvailable) {
      return;
    }

    bridgesAvailable = false;
    setEnabled(false);
    React.warn("Pathfinder Budget disabled due to runtime incompatibility: " + e.getClass().getSimpleName() + ": " + e.getMessage());
  }

  private double sampleTickMs() {
    try {
      Sampler sampler = React.sampler(SamplerTickTime.ID);
      return sampler == null ? 0D : sampler.sample();
    } catch (Throwable ignored) {
      return 0D;
    }
  }
}
