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
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.NearbyPlayerIndexController.PlayerViewSnapshot;
import art.arcane.react.content.feature.PlayerChunkBudget.Budgets;
import art.arcane.react.content.feature.PlayerChunkBudget.ChunkPosition;
import art.arcane.react.content.feature.PlayerChunkBudget.Distances;
import art.arcane.react.content.feature.PlayerChunkBudget.Limits;
import art.arcane.react.content.feature.PlayerChunkBudget.Recovery;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.model.MinMax;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.DistanceSupport;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RollingSequence;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
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
  @ConfigDoc(value = "Enables per-world player chunk budgets with overlap counted once.", impact = "Disabled by default. When enabled, budgets can lower the existing distance targets and recovery increases are gradual.")
  private boolean playerChunkBudgetEnabled = false;
  @ConfigDoc(value = "Maximum estimated player-ticking chunks per world; minimum 1.", impact = "Overlapping simulation footprints count once. Configured minimum distances take priority when the budget cannot be met.")
  private long playerTickingChunkBudget = 6000L;
  @ConfigDoc(value = "Maximum estimated view-only chunks per world; minimum 1.", impact = "Counts the view footprint outside the simulation footprint. This predicts player demand, not actual loaded chunks or plugin tickets.")
  private long playerViewOnlyChunkBudget = 6000L;
  @ConfigDoc(value = "Consecutive healthy budget evaluations required before increasing a distance; minimum 1.", impact = "Evaluations follow updateCooldownSeconds. Unknown tick telemetry or an unready player index resets recovery.")
  private int budgetRecoveryChecks = 3;
  @ConfigDoc(value = "Maximum distance increase after successful budget recovery, in chunks; clamped to 1 through 32.", impact = "Smaller steps limit chunk-loading bursts while distances recover. Only applies when player chunk budgeting is enabled.")
  private int budgetRecoveryStep = 1;
  private transient Map<UUID, Recovery> budgetRecovery;
  private transient long lastBudgetEvaluationMs;
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

    Distances targets = interpolationTargets(tickAverage, players);
    return applyDistances(world, targets, generation);
  }

  private Distances interpolationTargets(double tickAverage, int players) {
    int view = M.min(lerp(lerpTickTime, viewDistance, tickAverage),
        lerp(lerpPlayersOnline, viewDistance, players)).intValue();
    int simulation = M.min(lerp(lerpTickTime, simulationDistance, tickAverage),
        lerp(lerpPlayersOnline, simulationDistance, players)).intValue();
    return new Distances(view, Math.min(simulation, view));
  }

  private boolean applyDistances(World world, Distances targets, long generation) throws Exception {
    int vd = world.getViewDistance();
    int sd = world.getSimulationDistance();
    int newVD = targets.view();
    int newSD = targets.simulation();

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
      budgetRecovery = new HashMap<>();
      lastBudgetEvaluationMs = 0L;
    }

    if (!DistanceSupport.supportsWorldDistanceSetters()) {
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
      normalizeDistances(viewDistance, Bukkit.getServer().getViewDistance());
      normalizeDistances(simulationDistance, Math.min(Bukkit.getServer().getSimulationDistance(), (int) viewDistance.getMax()));
      viewDistance.setMin(Math.max(viewDistance.getMin(), simulationDistance.getMin()));
      ttAvg = new RollingSequence(10);
      if (!playerChunkBudgetEnabled) {
        ttAvg.put(0);
      }
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
    double tickValue = playerChunkBudgetEnabled ? availableTickTime() : sample(SamplerTickTime.ID);
    if (playerChunkBudgetEnabled && !Double.isFinite(tickValue)) {
      resetBudgetRecovery(generation);
      return;
    }
    double tickAverage;
    synchronized (tickAverages) {
      if (!isActive(generation)) {
        return;
      }
      tickAverages.put(tickValue);
      tickAverage = tickAverages.getAverage();
    }
    long now = System.currentTimeMillis();
    if (now - activatedAtMs < Math.max(0, warmupSeconds) * 1000L) {
      return;
    }
    synchronized (lifecycleLock) {
      if (!isActive(generation) || !updateQueued.compareAndSet(false, true)) {
        return;
      }
    }

    try {
      J.sync(() -> beginUpdate(now, tickAverage, tickValue, generation));
    } catch (RuntimeException failure) {
      releaseUpdate(generation);
      throw failure;
    }
  }

  private void beginUpdate(long now, double tickAverage, double tickValue, long generation) {
    boolean dispatched = false;
    try {
      if (playerChunkBudgetEnabled) {
        BudgetEvaluation evaluation = captureBudgetEvaluation(now, tickAverage, tickValue, generation);
        if (evaluation != null) {
          J.a(() -> calculateBudget(evaluation));
          dispatched = true;
        }
      } else {
        updateWorlds(now, tickAverage, generation);
      }
    } finally {
      if (!dispatched) {
        releaseUpdate(generation);
      }
    }
  }

  private BudgetEvaluation captureBudgetEvaluation(long now, double tickAverage, double tickValue, long generation) {
    synchronized (lifecycleLock) {
      if (!isActive(generation)) {
        return null;
      }
      NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
      if (index == null || !index.isInitialSeedReady()) {
        budgetRecovery.clear();
        return null;
      }
      if (now - lastBudgetEvaluationMs < Math.max(1L, updateCooldownSeconds) * 1000L) {
        return null;
      }
      List<PlayerViewSnapshot> snapshots = index.playerSnapshots();
      Map<UUID, List<ChunkPosition>> positions = new HashMap<>();
      for (PlayerViewSnapshot snapshot : snapshots) {
        if (!Double.isFinite(snapshot.x()) || !Double.isFinite(snapshot.z())) {
          budgetRecovery.clear();
          return null;
        }
        positions.computeIfAbsent(snapshot.worldId(), ignored -> new ArrayList<>()).add(new ChunkPosition(
            (int) Math.floor(snapshot.x() / 16D), (int) Math.floor(snapshot.z() / 16D)));
      }
      Distances ceiling = interpolationTargets(tickAverage, Bukkit.getOnlinePlayers().size());
      List<WorldBudgetRequest> requests = new ArrayList<>();
      for (World world : Bukkit.getWorlds()) {
        requests.add(new WorldBudgetRequest(world, new Distances(world.getViewDistance(), world.getSimulationDistance()),
            ceiling, List.copyOf(positions.getOrDefault(world.getUID(), List.of()))));
      }
      lastBudgetEvaluationMs = now;
      return new BudgetEvaluation(now, generation,
          tickValue <= lerpTickTime.getMin() && tickAverage <= lerpTickTime.getMin(),
          new Limits((int) viewDistance.getMin(), (int) simulationDistance.getMin()),
          new Budgets(playerTickingChunkBudget, playerViewOnlyChunkBudget), List.copyOf(requests));
    }
  }

  private void calculateBudget(BudgetEvaluation evaluation) {
    boolean dispatched = false;
    try {
      if (!isActive(evaluation.generation())) {
        return;
      }
      List<WorldBudgetResult> results = new ArrayList<>(evaluation.requests().size());
      for (WorldBudgetRequest request : evaluation.requests()) {
        Distances target = PlayerChunkBudget.constrain(request.positions(), evaluation.limits(), request.ceiling(), evaluation.budgets(), request.current().simulation());
        results.add(new WorldBudgetResult(request.world(), request.current(), target));
      }
      J.sync(() -> applyBudget(evaluation, results));
      dispatched = true;
    } catch (Throwable failure) {
      React.reportError("Failed to calculate player chunk budgets", failure);
    } finally {
      if (!dispatched) {
        releaseUpdate(evaluation.generation());
      }
    }
  }

  private void applyBudget(BudgetEvaluation evaluation, List<WorldBudgetResult> results) {
    try {
      synchronized (lifecycleLock) {
        if (!isActive(evaluation.generation())) {
          return;
        }
        NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
        double currentTickTime = availableTickTime();
        if (index == null || !index.isInitialSeedReady() || !Double.isFinite(currentTickTime)) {
          budgetRecovery.clear();
          return;
        }
        for (WorldBudgetResult result : results) {
          World world = result.world();
          UUID worldId = world.getUID();
          if (Bukkit.getWorld(worldId) != world || world.getViewDistance() != result.current().view()
              || world.getSimulationDistance() != result.current().simulation()) {
            budgetRecovery.remove(worldId);
            continue;
          }
          Recovery recovery = budgetRecovery.computeIfAbsent(worldId, ignored -> new Recovery());
          Distances current = new Distances(
              Math.max(evaluation.limits().minimumView(), result.current().view()),
              Math.max(evaluation.limits().minimumSimulation(), result.current().simulation()));
          Distances next = recovery.next(current, result.target(),
              evaluation.healthy() && currentTickTime <= lerpTickTime.getMin(), budgetRecoveryChecks, budgetRecoveryStep);
          if (applyDistances(world, next, evaluation.generation())) {
            lastUpdate.put(worldId, evaluation.evaluatedAt());
          }
        }
      }
    } catch (Throwable failure) {
      React.reportError("Failed to apply player chunk budgets", failure);
    } finally {
      releaseUpdate(evaluation.generation());
    }
  }

  private double availableTickTime() {
    Sampler sampler = React.sampler(SamplerTickTime.ID);
    if (sampler == null) {
      return Double.NaN;
    }
    double value = sampler.sample();
    return sampler.isSampleAvailable() && value >= 0D ? value : Double.NaN;
  }

  private void resetBudgetRecovery(long generation) {
    synchronized (lifecycleLock) {
      if (isActive(generation)) {
        budgetRecovery.clear();
      }
    }
  }

  private void releaseUpdate(long generation) {
    synchronized (lifecycleLock) {
      if (generation == lifecycleGeneration.get()) {
        updateQueued.set(false);
      }
    }
  }

  private static void normalizeDistances(MinMax range, int serverCeiling) {
    double maximum = Double.isFinite(range.getMax()) ? range.getMax() : serverCeiling;
    maximum = Math.max(2D, Math.min(Math.min(32, serverCeiling), maximum));
    double minimum = Double.isFinite(range.getMin()) ? range.getMin() : 2D;
    range.setMax(Math.floor(maximum));
    range.setMin(Math.min(range.getMax(), Math.max(2D, Math.ceil(minimum))));
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

  private record WorldBudgetRequest(World world, Distances current, Distances ceiling, List<ChunkPosition> positions) {
  }

  private record WorldBudgetResult(World world, Distances current, Distances target) {
  }

  private record BudgetEvaluation(long evaluatedAt, long generation, boolean healthy, Limits limits,
                                  Budgets budgets, List<WorldBudgetRequest> requests) {
  }

  private record WorldDistanceBaseline(World world, int viewDistance, int simulationDistance) {
  }
}
