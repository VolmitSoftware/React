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
import art.arcane.react.model.MinMax;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldDistanceSupport;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RollingSequence;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Dynamic View Distance feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureDynamicViewDistance extends ReactFeature implements Listener {
  public static final String ID = "dynamic-view-distance";
  private static final long RESTORE_TIMEOUT_SECONDS = 30L;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cooldown for update cooldown in dynamic view distance (seconds).", impact = "Higher values reduce repeat frequency; lower values allow reactions more often.")
  public int updateCooldownSeconds = 120;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Grace period after activation before dynamic view distance touches any world (seconds).", impact = "Prevents startup tick spikes from slamming view distance to the floor before the server settles; raise it if your server takes longer to warm up.")
  private int warmupSeconds = 45;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Interpolation range used by dynamic view distance to map observed load into target values.", impact = "Wider ranges allow broader adaptation; tighter ranges keep adjustments more conservative.")
  private MinMax viewDistance = new MinMax(6, 16);
  @art.arcane.react.util.project.config.ConfigDoc(value = "Interpolation range used by dynamic view distance to map observed load into target values.", impact = "Wider ranges allow broader adaptation; tighter ranges keep adjustments more conservative.")
  private MinMax simulationDistance = new MinMax(4, 10);
  @art.arcane.react.util.project.config.ConfigDoc(value = "Interpolation range used by dynamic view distance to map observed load into target values.", impact = "Wider ranges allow broader adaptation; tighter ranges keep adjustments more conservative.")
  private MinMax lerpTickTime = new MinMax(45, 140);
  @art.arcane.react.util.project.config.ConfigDoc(value = "Interpolation range used by dynamic view distance to map observed load into target values.", impact = "Wider ranges allow broader adaptation; tighter ranges keep adjustments more conservative.")
  private MinMax lerpPlayersOnline = new MinMax(3, 100);
  private transient RollingSequence ttAvg;
  private transient long activatedAtMs;
  private transient Map<UUID, Long> lastUpdate;
  private transient Map<UUID, WorldDistanceBaseline> originalDistances;
  private transient boolean supportsWorldDistanceSetters;
  private transient boolean warnedRuntimeFailure;
  private transient Method setViewDistanceMethod;
  private transient Method setSimulationDistanceMethod;
  private transient final AtomicBoolean updateQueued = new AtomicBoolean(false);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final Object lifecycleLock = new Object();
  private transient volatile boolean active;

  public FeatureDynamicViewDistance() {
    super(ID);
  }

  private boolean updateWorld(World world, double tickAverage, int players, long generation) throws Exception {
    if (!isActive(generation)) {
      return false;
    }

    int vd = world.getViewDistance();
    int sd = world.getSimulationDistance();

    int newVD = M.min(lerp(lerpTickTime, viewDistance, tickAverage),
        lerp(lerpPlayersOnline, viewDistance, players)).intValue();
    int newSD = M.min(lerp(lerpTickTime, simulationDistance, tickAverage),
        lerp(lerpPlayersOnline, simulationDistance, players)).intValue();
    newSD = Math.min(newSD, newVD);

    if (vd == newVD && sd == newSD) {
      return false;
    }

    UUID worldId = world.getUID();
    originalDistances.putIfAbsent(worldId, new WorldDistanceBaseline(world, vd, sd));

    List<String> m = new ArrayList<>();
    if (vd != newVD) {
      if (!isActive(generation)) {
        return false;
      }
      m.add("View Distance: " + vd + " -> " + newVD);
      setViewDistanceMethod.invoke(world, newVD);
    }

    if (sd != newSD) {
      if (!isActive(generation)) {
        return !m.isEmpty();
      }
      m.add("Simulation Distance: " + sd + " -> " + newSD);
      setSimulationDistanceMethod.invoke(world, newSD);
    }

    if (!m.isEmpty()) {
      React.verbose(() -> world.getName() + ": " + String.join(" ", m));
      return true;
    }

    return false;
  }

  public static double lerp(MinMax range, MinMax output, double inRange) {
    return Math.max(Math.min(output.getMax(),
            M.lerp(output.getMax(), output.getMin(), M.lerpInverse(range.getMin(), range.getMax(), inRange))),
        output.getMin());
  }

  @Override
  public void onActivate() {
    long generation;
    synchronized (lifecycleLock) {
      active = false;
      generation = lifecycleGeneration.incrementAndGet();
      updateQueued.set(false);
      supportsWorldDistanceSetters = false;
      warnedRuntimeFailure = false;
      ttAvg = null;
      lastUpdate = new ConcurrentHashMap<>();
      originalDistances = new ConcurrentHashMap<>();
    }

    if (!WorldDistanceSupport.supportsWorldDistanceSetters()) {
      setEnabled(false);
      React.warn("Dynamic View Distance disabled: this server software does not expose world distance setters. Use Paper/Purpur to enable this feature.");
      return;
    }
    try {
      setViewDistanceMethod = World.class.getMethod("setViewDistance", int.class);
      setSimulationDistanceMethod = World.class.getMethod("setSimulationDistance", int.class);
    } catch (NoSuchMethodException e) {
      supportsWorldDistanceSetters = false;
      setEnabled(false);
      React.warn("Dynamic View Distance disabled: world distance setters are not resolvable on this server software.");
      return;
    }

    synchronized (lifecycleLock) {
      if (generation != lifecycleGeneration.get()) {
        return;
      }
      viewDistance.setMax(Math.min(viewDistance.getMax(), Bukkit.getServer().getViewDistance()));
      simulationDistance.setMax(Math.min(simulationDistance.getMax(), Bukkit.getServer().getSimulationDistance()));
      ttAvg = new RollingSequence(10);
      ttAvg.put(0);
      activatedAtMs = System.currentTimeMillis();
      supportsWorldDistanceSetters = true;
      active = true;
    }
  }

  @Override
  public void onDeactivate() {
    Map<UUID, WorldDistanceBaseline> retiredDistances;
    synchronized (lifecycleLock) {
      active = false;
      lifecycleGeneration.incrementAndGet();
      updateQueued.set(false);
      supportsWorldDistanceSetters = false;
      retiredDistances = originalDistances;
      originalDistances = new ConcurrentHashMap<>();
    }
    try {
      restoreAuthoritatively(retiredDistances);
    } catch (RuntimeException failure) {
      originalDistances.putAll(retiredDistances);
      throw failure;
    }
  }

  @Override
  public int getTickInterval() {
    return supportsWorldDistanceSetters ? 1000 : 0;
  }

  @Override
  public void onTick() {
    long generation = lifecycleGeneration.get();
    RollingSequence tickAverages = ttAvg;
    if (!isActive(generation) || tickAverages == null || lastUpdate == null) {
      return;
    }
    double tickAverage;
    synchronized (tickAverages) {
      if (!isActive(generation)) {
        return;
      }
      tickAverages.put(sample(SamplerTickTime.ID));
      tickAverage = tickAverages.getAverage();
    }
    long now = System.currentTimeMillis();
    if (now - activatedAtMs < Math.max(0, warmupSeconds) * 1000L) {
      return;
    }
    if (!updateQueued.compareAndSet(false, true)) {
      return;
    }

    J.sync(() -> {
      try {
        updateWorlds(now, tickAverage, generation);
      } finally {
        if (generation == lifecycleGeneration.get()) {
          updateQueued.set(false);
        }
      }
    });
  }

  private void updateWorlds(long now, double tickAverage, long generation) {
    synchronized (lifecycleLock) {
      if (!isActive(generation)) {
        return;
      }

      long cooldownMs = Math.max(1L, updateCooldownSeconds) * 1000L;
      int players = Bukkit.getOnlinePlayers().size();
      for (World world : Bukkit.getWorlds()) {
        if (!isActive(generation)) {
          return;
        }

        UUID worldId = world.getUID();
        if (lastUpdate.getOrDefault(worldId, 0L) >= now - cooldownMs) {
          continue;
        }

        try {
          if (updateWorld(world, tickAverage, players, generation) && isActive(generation)) {
            lastUpdate.put(worldId, now);
          }
        } catch (Throwable e) {
          if (!warnedRuntimeFailure) {
            warnedRuntimeFailure = true;
            setEnabled(false);
            React.reportError("Dynamic View Distance disabled due to runtime incompatibility: "
                + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
          }
          return;
        }
      }
    }
  }

  private void restoreAuthoritatively(Map<UUID, WorldDistanceBaseline> retiredDistances) {
    if (retiredDistances == null || retiredDistances.isEmpty()) {
      return;
    }

    if (J.isPrimaryThread()) {
      synchronized (lifecycleLock) {
        requireCompleteRestore(retiredDistances);
      }
      return;
    }

    CountDownLatch completed = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    boolean scheduled = FoliaScheduler.runGlobal(React.instance, () -> {
      try {
        synchronized (lifecycleLock) {
          requireCompleteRestore(retiredDistances);
        }
      } catch (Throwable throwable) {
        failure.set(throwable);
      } finally {
        completed.countDown();
      }
    });
    if (!scheduled) {
      throw new IllegalStateException("Failed to schedule dynamic view distance restoration");
    }

    try {
      if (!completed.await(RESTORE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out restoring dynamic view distance state");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while restoring dynamic view distance state", exception);
    }

    Throwable restoreFailure = failure.get();
    if (restoreFailure instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (restoreFailure != null) {
      throw new IllegalStateException("Failed to restore dynamic view distance state", restoreFailure);
    }
  }

  private void requireCompleteRestore(Map<UUID, WorldDistanceBaseline> retiredDistances) {
    int restored = 0;
    Throwable failure = null;
    for (Map.Entry<UUID, WorldDistanceBaseline> entry : retiredDistances.entrySet()) {
      WorldDistanceBaseline baseline = entry.getValue();
      try {
        setViewDistanceMethod.invoke(baseline.world(), baseline.viewDistance());
        setSimulationDistanceMethod.invoke(baseline.world(), baseline.simulationDistance());
        if (retiredDistances.remove(entry.getKey(), baseline)) {
          restored++;
        }
      } catch (Throwable throwable) {
        if (failure == null) {
          failure = throwable;
        } else {
          failure.addSuppressed(throwable);
        }
      }
    }

    if (!retiredDistances.isEmpty()) {
      throw new IllegalStateException(
          "Dynamic view distance retained " + retiredDistances.size() + " unrestored worlds",
          failure
      );
    }
    if (restored > 0) {
      React.verbose("Dynamic view distance restored original settings for " + restored + " worlds");
    }
  }

  private boolean isActive(long generation) {
    return active
        && supportsWorldDistanceSetters
        && generation == lifecycleGeneration.get();
  }

  private record WorldDistanceBaseline(World world, int viewDistance, int simulationDistance) {
  }
}
