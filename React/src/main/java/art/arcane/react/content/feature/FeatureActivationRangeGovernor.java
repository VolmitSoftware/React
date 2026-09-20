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
import art.arcane.react.api.feature.PressureGate;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import art.arcane.volmlib.nativelib.monitor.EntityRangeSettings;
import art.arcane.volmlib.nativelib.monitor.EntityRangeSettings.Range;

import java.util.EnumSet;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Activation Range Governor feature. Temporarily scales down per-world entity activation ranges while the server is under sustained pressure, so the server itself deactivates distant entities instead of ticking them. Unlike dynamic-activation-range (which samples and pauses entities one by one), these ranges are read by the server every tick and apply to all entities instantly. Ranges and villager ticking are restored once the server recovers.")
public class FeatureActivationRangeGovernor extends ReactFeature {
  public static final String ID = "activation-range-governor";
  private static final Set<Range> GOVERNED_RANGES = EnumSet.of(
      Range.ACTIVATION_ANIMAL,
      Range.ACTIVATION_MONSTER,
      Range.ACTIVATION_RAIDER,
      Range.ACTIVATION_MISC,
      Range.ACTIVATION_WATER,
      Range.ACTIVATION_VILLAGER,
      Range.ACTIVATION_FLYING_MONSTER);
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for activation range governor in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 2000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before the governor engages.", impact = "Lower values shrink activation ranges earlier; higher values reserve it for heavier load.")
  private double engageTickTimeMs = 55;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds the server must stay below before the governor releases.", impact = "Lower values hold the reduction longer for stability; higher values restore full ranges sooner.")
  private double releaseTickTimeMs = 42;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained pressure duration required before engaging (milliseconds).", impact = "Higher values ignore short spikes; lower values engage faster.")
  private long sustainEngageMs = 6000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained recovery duration required before releasing (milliseconds).", impact = "Higher values avoid flapping between states; lower values restore ranges sooner.")
  private long sustainReleaseMs = 30_000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to animal activation range while engaged.", impact = "Lower values deactivate distant animals sooner during pressure windows.")
  private double animalRangeFactor = 0.5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to monster activation range while engaged.", impact = "Lower values deactivate distant monsters sooner; mobs freeze visibly until approached.")
  private double monsterRangeFactor = 0.6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to raider activation range while engaged.", impact = "Lower values shed raider AI cost but can slow active raids; kept conservative by default.")
  private double raiderRangeFactor = 0.8;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to misc-entity activation range while engaged.", impact = "Lower values deactivate distant items, projectiles, and ambient entities sooner.")
  private double miscRangeFactor = 0.5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to water-mob activation range while engaged.", impact = "Lower values deactivate distant water mobs sooner during pressure windows.")
  private double waterRangeFactor = 0.5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to villager activation range while engaged.", impact = "Lower values shed villager brain and POI cost, the most expensive entity class; distant villagers idle until approached.")
  private double villagerRangeFactor = 0.5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to flying-monster activation range while engaged.", impact = "Lower values deactivate distant phantoms and ghasts sooner during pressure windows.")
  private double flyingMonsterRangeFactor = 0.6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum activation range in blocks after scaling.", impact = "Higher values cap how aggressively ranges shrink; lower values allow deeper cuts.")
  private int minimumRangeBlocks = 8;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Also suspends ticking of inactive villagers while engaged.", impact = "Enable to stop inactive villager restock/brain ticking during pressure windows; disable to only scale ranges.")
  private boolean suspendInactiveVillagerTicking = true;
  private transient NativeWorldAccess nativeAccess;
  private transient Set<Range> rangeTypes;
  private transient Map<UUID, Map<Range, Integer>> baselinesByWorld;
  private transient Map<UUID, Boolean> villagerTickBaselines;
  private transient boolean resolved;
  private transient boolean supported;
  private transient final PressureGate gate = new PressureGate();
  private transient final Object worldMutationLock = new Object();
  private transient final Deque<RetiredWorldState> pendingReleases = new ArrayDeque<>();
  private transient volatile AtomicInteger mutationRequests = new AtomicInteger(0);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient volatile boolean requestedEngaged;
  private transient volatile boolean active;

  public FeatureActivationRangeGovernor() {
    super(ID);
  }

  @Override
  public void onActivate() {
    synchronized (worldMutationLock) {
      active = false;
      lifecycleGeneration.incrementAndGet();
      baselinesByWorld = new ConcurrentHashMap<>();
      villagerTickBaselines = new ConcurrentHashMap<>();
      resolved = false;
      supported = false;
      gate.reset();
      mutationRequests = new AtomicInteger(0);
      requestedEngaged = false;
      active = true;
    }
  }

  @Override
  public void onDeactivate() {
    Map<UUID, Map<Range, Integer>> retiredBaselines;
    Map<UUID, Boolean> retiredVillagerBaselines;
    RetiredWorldState retiredState;
    synchronized (worldMutationLock) {
      active = false;
      lifecycleGeneration.incrementAndGet();
      gate.reset();
      requestedEngaged = false;
      mutationRequests = new AtomicInteger(0);
      retiredBaselines = baselinesByWorld;
      retiredVillagerBaselines = villagerTickBaselines;
      retiredState = new RetiredWorldState(retiredBaselines, retiredVillagerBaselines);
      if (retiredState.hasState()) {
        pendingReleases.addLast(retiredState);
      }
    }

    if (!retiredState.hasState()) {
      return;
    }

    if (J.isPrimaryThread()) {
      releaseRetiredState(retiredState);
      return;
    }

    J.sync(() -> releaseRetiredState(retiredState));
  }

  @Override
  public int getTickInterval() {
    return Math.max(1000, tickIntervalMS);
  }

  @Override
  public void onTick() {
    long generation = lifecycleGeneration.get();
    if (!isActive(generation)) {
      return;
    }

    double tickMs = sample(SamplerTickTime.ID);
    if (!isActive(generation)) {
      return;
    }
    boolean pressure = !(tickMs < engageTickTimeMs);
    boolean calm = !(tickMs > releaseTickTimeMs);
    long now = System.currentTimeMillis();
    boolean wasEngaged;
    boolean nowEngaged;
    synchronized (worldMutationLock) {
      if (!isActive(generation)) {
        return;
      }
      wasEngaged = gate.isEngaged();
      nowEngaged = gate.update(now, pressure, calm, sustainEngageMs, sustainReleaseMs);
    }

    if (!wasEngaged && nowEngaged) {
      requestWorldState(true, generation);
    } else if (wasEngaged && !nowEngaged) {
      requestWorldState(false, generation);
    }
  }

  private void requestWorldState(boolean engaged, long generation) {
    AtomicInteger requests;
    synchronized (worldMutationLock) {
      if (!isActive(generation)) {
        return;
      }
      requestedEngaged = engaged;
      requests = mutationRequests;
    }

    if (J.isPrimaryThread()) {
      applyRequestedWorldState(generation);
      return;
    }
    if (requests.getAndIncrement() != 0) {
      return;
    }

    J.sync(() -> {
      if (isActive(generation)) {
        drainWorldStateRequests(requests, generation);
      }
    });
  }

  private void drainWorldStateRequests(AtomicInteger requests, long generation) {
    int completed = 1;
    while (true) {
      if (!isActive(generation)) {
        requests.set(0);
        return;
      }
      applyRequestedWorldState(generation);
      int remaining = requests.addAndGet(-completed);
      if (remaining == 0) {
        return;
      }
      completed = remaining;
    }
  }

  private void applyRequestedWorldState(long generation) {
    synchronized (worldMutationLock) {
      if (!isActive(generation)) {
        return;
      }
      releasePendingState();
      if (!isActive(generation)) {
        return;
      }
      if (requestedEngaged) {
        engage();
      } else {
        release(baselinesByWorld, villagerTickBaselines);
      }
    }
  }

  private void engage() {
    if (!resolveSupport()) {
      return;
    }

    int governed = 0;
    for (World world : Bukkit.getWorlds()) {
      try {
        EntityRangeSettings config = nativeAccess.entityRanges(world);
        if (config == null) {
          continue;
        }

        Map<Range, Integer> baselines = new HashMap<>();
        for (Range type : rangeTypes) {
          int base = config.range(type);
          if (base <= 0) {
            continue;
          }

          int target = Math.max(Math.max(1, minimumRangeBlocks), (int) Math.round(base * factorFor(type)));
          if (target >= base) {
            continue;
          }

          baselines.put(type, base);
          config.range(type, target);
          governed++;
        }

        if (!baselines.isEmpty()) {
          baselinesByWorld.put(world.getUID(), baselines);
        }

        if (suspendInactiveVillagerTicking) {
          boolean base = config.tickInactiveVillagers();
          if (base) {
            villagerTickBaselines.put(world.getUID(), true);
            config.tickInactiveVillagers(false);
            governed++;
          }
        }
      } catch (Throwable e) {
        failRuntime(e);
        return;
      }
    }

    if (governed > 0) {
      React.verbose("Activation range governor engaged: scaled " + governed + " activation settings across " + baselinesByWorld.size() + " worlds");
    }
  }

  private void release(
      Map<UUID, Map<Range, Integer>> rangeBaselines,
      Map<UUID, Boolean> inactiveVillagerBaselines
  ) {
    if (rangeBaselines == null) {
      return;
    }

    int restored = 0;
    for (Map.Entry<UUID, Map<Range, Integer>> worldEntry : rangeBaselines.entrySet()) {
      World world = Bukkit.getWorld(worldEntry.getKey());
      if (world == null) {
        continue;
      }

      try {
        EntityRangeSettings config = nativeAccess.entityRanges(world);
        if (config == null) {
          continue;
        }

        for (Map.Entry<Range, Integer> entry : worldEntry.getValue().entrySet()) {
          config.range(entry.getKey(), entry.getValue());
          restored++;
        }
      } catch (Throwable e) {
        failRuntime(e);
        return;
      }
    }

    if (inactiveVillagerBaselines == null) {
      rangeBaselines.clear();
      return;
    }

    for (Map.Entry<UUID, Boolean> entry : inactiveVillagerBaselines.entrySet()) {
      World world = Bukkit.getWorld(entry.getKey());
      if (world == null) {
        continue;
      }

      try {
        EntityRangeSettings config = nativeAccess.entityRanges(world);
        if (config != null) {
          config.tickInactiveVillagers(entry.getValue());
          restored++;
        }
      } catch (Throwable e) {
        failRuntime(e);
        return;
      }
    }

    rangeBaselines.clear();
    inactiveVillagerBaselines.clear();
    if (restored > 0) {
      React.verbose("Activation range governor released: restored " + restored + " activation settings");
    }
  }

  private void releaseRetiredState(RetiredWorldState retiredState) {
    synchronized (worldMutationLock) {
      if (!pendingReleases.remove(retiredState)) {
        return;
      }
      release(retiredState.rangeBaselines, retiredState.inactiveVillagerBaselines);
    }
  }

  private void releasePendingState() {
    RetiredWorldState retiredState;
    while ((retiredState = pendingReleases.pollFirst()) != null) {
      release(retiredState.rangeBaselines, retiredState.inactiveVillagerBaselines);
    }
  }

  private boolean isActive(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  private double factorFor(Range type) {
    return switch (type) {
      case ACTIVATION_ANIMAL -> animalRangeFactor;
      case ACTIVATION_MONSTER -> monsterRangeFactor;
      case ACTIVATION_RAIDER -> raiderRangeFactor;
      case ACTIVATION_MISC -> miscRangeFactor;
      case ACTIVATION_WATER -> waterRangeFactor;
      case ACTIVATION_VILLAGER -> villagerRangeFactor;
      default -> flyingMonsterRangeFactor;
    };
  }

  private boolean resolveSupport() {
    if (resolved) {
      return supported;
    }

    resolved = true;
    List<World> worlds = Bukkit.getWorlds();
    if (worlds.isEmpty()) {
      resolved = false;
      return false;
    }

    try {
      nativeAccess = NativeAdapters.find(NativeWorldAccess.class).orElse(null);
      if (nativeAccess == null) {
        React.warn("Activation Range Governor disabled: native world settings are unavailable.");
        setEnabled(false);
        return false;
      }
      rangeTypes = EnumSet.copyOf(GOVERNED_RANGES);
      rangeTypes.retainAll(nativeAccess.entityRanges(worlds.getFirst()).supportedRanges());
      supported = true;
      return true;
    } catch (Throwable e) {
      failRuntime(e);
      return false;
    }
  }

  private void failRuntime(Throwable e) {
    supported = false;
    setEnabled(false);
    React.reportError("Activation Range Governor disabled due to runtime incompatibility: "
        + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
  }

  private static final class RetiredWorldState {
    private final Map<UUID, Map<Range, Integer>> rangeBaselines;
    private final Map<UUID, Boolean> inactiveVillagerBaselines;

    private RetiredWorldState(
        Map<UUID, Map<Range, Integer>> rangeBaselines,
        Map<UUID, Boolean> inactiveVillagerBaselines
    ) {
      this.rangeBaselines = rangeBaselines;
      this.inactiveVillagerBaselines = inactiveVillagerBaselines;
    }

    private boolean hasState() {
      return rangeBaselines != null && !rangeBaselines.isEmpty()
          || inactiveVillagerBaselines != null && !inactiveVillagerBaselines.isEmpty();
    }
  }
}
