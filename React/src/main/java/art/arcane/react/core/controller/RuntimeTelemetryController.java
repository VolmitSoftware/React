package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.core.telemetry.runtime.FoliaRegionTps;
import art.arcane.react.core.telemetry.runtime.JfrTelemetry;
import art.arcane.react.core.telemetry.runtime.PaperPathfindingDecisions;
import art.arcane.react.core.telemetry.runtime.RegionalHealth;
import art.arcane.react.core.telemetry.runtime.RuntimeMetric;
import art.arcane.react.core.telemetry.runtime.RuntimeMetricSampler;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Samples Folia player locations and optional bounded JDK Flight Recorder events. JFR captures protocol traffic totals, chunk generation stages, storage activity, and waits above the configured threshold.")
public final class RuntimeTelemetryController implements IController {
  private static final long INTERVAL_MS = 1_000L;
  private static final long MAX_SNAPSHOT_AGE_MS = 5_000L;
  @ConfigDoc(value = "Enable Folia region TPS sampling at player locations.", impact = "Queries a bounded rotating set of cached player locations once per second without loading chunks.")
  private boolean regionalEnabled = true;
  @ConfigDoc(value = "Maximum player locations sampled each second, clamped from 1 to 512. Observations expire after five seconds.", impact = "Higher values improve player coverage at the cost of more region TPS queries.")
  private int playerAnchorsPerRefresh = 64;
  @ConfigDoc(value = "Enable selective JFR telemetry. Disabled by default. Exports event summaries without addresses, file paths, or stack traces.", impact = "Adds JFR recording work with 30-second and 64-MiB retention limits. Aggregate series reset each second.")
  private boolean jfrEnabled = false;
  @ConfigDoc(value = "Minimum duration in milliseconds for JVM file IO, monitor entry, monitor wait, and thread park events, clamped from 1 to 1000. Chunk generation stages use no duration threshold.", impact = "Lower thresholds emit more JVM events. These metrics describe only events meeting this threshold.")
  private long jfrSlowThresholdMs = 10L;
  private transient final Object lifecycleLock = new Object();
  private transient final AtomicLong jfrGeneration = new AtomicLong();
  private transient final AtomicLong configurationRevision = new AtomicLong();
  private transient final RegionalHealth regionalHealth = new RegionalHealth();
  private transient final List<String> registeredIds = new ArrayList<>();
  private transient volatile Options options = new Options(true, 64, false, 10L);
  private transient volatile boolean active;
  private transient volatile RegionalHealth.Snapshot regionalSnapshot = RegionalHealth.Snapshot.EMPTY;
  private transient volatile JfrTelemetry.Snapshot jfrSnapshot = JfrTelemetry.Snapshot.EMPTY;
  private transient volatile String regionalReason = "warming-up";
  private transient volatile String jfrReason = "disabled";
  private transient ScheduledExecutorService executor;
  private transient FoliaRegionTps regionSource;
  private transient volatile JfrTelemetry jfr;
  private transient Options appliedOptions;
  private transient long appliedRevision = -1L;
  private transient PaperPathfindingDecisions pathfinding;
  private transient volatile PaperPathfindingDecisions.Snapshot pathfindingSnapshot = PaperPathfindingDecisions.Snapshot.EMPTY;
  private transient long pathfindingAtNanos;

  @Override
  public String getId() {
    return "runtime-telemetry";
  }

  @Override
  public String getName() {
    return "Runtime Telemetry";
  }

  @Override
  public void start() {
    options = configuredOptions();
    appliedOptions = null;
    appliedRevision = -1L;
    active = true;
    registeredIds.clear();
    regionalHealth.clear();
    regionalSnapshot = RegionalHealth.Snapshot.EMPTY;
    jfrSnapshot = JfrTelemetry.Snapshot.EMPTY;
  }

  @Override
  public void postStart() {
    SampleController controller = React.controller(SampleController.class);
    if (controller != null) {
      register(controller, new RuntimeMetricSampler.Definition("region-tps-lowest", "Lowest Observed Player Region TPS", "TPS", 5_000L));
      register(controller, new RuntimeMetricSampler.Definition("region-players-below-18-tps", "Observed Players Below 18 TPS", "players", 5_000L));
      register(controller, new RuntimeMetricSampler.Definition("region-players-below-15-tps", "Observed Players Below 15 TPS", "players", 5_000L));
      register(controller, new RuntimeMetricSampler.Definition("region-player-coverage", "Region Player Coverage", "%", 5_000L));
      register(controller, new RuntimeMetricSampler.Definition("pathfinding-decisions-rate", "Pathfinding Decisions", "/s", 1_000L));
      register(controller, new RuntimeMetricSampler.Definition("pathfinding-cancelled-rate", "Cancelled Pathfinding Decisions", "/s", 1_000L));
      for (RuntimeMetricSampler.Definition definition : JfrTelemetry.definitions()) {
        register(controller, definition);
      }
    }
    try {
      Class.forName("com.destroystokyo.paper.event.entity.EntityPathfindEvent", false, getClass().getClassLoader());
      pathfinding = new PaperPathfindingDecisions();
      React.instance.registerListener(pathfinding);
      pathfindingAtNanos = System.nanoTime();
    } catch (ClassNotFoundException missing) {
      pathfinding = null;
    }
    executor = Executors.newSingleThreadScheduledExecutor(task -> Thread.ofPlatform().daemon(true).name("React Runtime Telemetry").unstarted(task));
    executor.scheduleWithFixedDelay(this::refresh, 0L, INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  @Override
  public void stop() {
    active = false;
    try {
      try {
        stopExecutor();
      } finally {
        closeJfr();
      }
    } finally {
      if (pathfinding != null) {
        React.instance.unregisterListener(pathfinding);
        pathfinding = null;
      }
      pathfindingSnapshot = PaperPathfindingDecisions.Snapshot.EMPTY;
      regionalSnapshot = RegionalHealth.Snapshot.EMPTY;
      jfrSnapshot = JfrTelemetry.Snapshot.EMPTY;
      SampleController controller = React.controller(SampleController.class);
      if (controller != null) {
        for (String id : registeredIds) {
          controller.unregisterSampler(id);
        }
      }
      registeredIds.clear();
    }
  }

  @Override
  public boolean applyConfigurationSnapshot(Object prepared) {
    if (!IController.super.applyConfigurationSnapshot(prepared)) {
      return false;
    }
    options = configuredOptions();
    configurationRevision.incrementAndGet();
    return true;
  }

  @Override
  public boolean reloadFromDisk(boolean overwriteOnReadFailure) {
    if (!IController.super.reloadFromDisk(overwriteOnReadFailure)) {
      return false;
    }
    options = configuredOptions();
    configurationRevision.incrementAndGet();
    return true;
  }

  public RegionalHealth.Snapshot regionalSnapshot() {
    return regionalSnapshot;
  }

  public JfrTelemetry.Snapshot jfrSnapshot() {
    return jfrSnapshot;
  }

  public RuntimeMetric metric(String id) {
    if (!active) {
      return RuntimeMetric.unavailable("stopped");
    }
    long nowMs = System.currentTimeMillis();
    if (id.startsWith("pathfinding-")) {
      PaperPathfindingDecisions.Snapshot snapshot = pathfindingSnapshot;
      boolean available = snapshot.sampledAtMs() > 0L && nowMs >= snapshot.sampledAtMs() && nowMs - snapshot.sampledAtMs() <= MAX_SNAPSHOT_AGE_MS;
      double value = id.equals("pathfinding-decisions-rate") ? snapshot.decisionsPerSecond() : snapshot.cancelledPerSecond();
      return new RuntimeMetric(value, snapshot.sampledAtMs(), available, available ? "" : "source-unavailable");
    }
    if (id.startsWith("region-")) {
      RegionalHealth.Snapshot snapshot = regionalSnapshot;
      if (!regionalReason.isEmpty()) {
        return RuntimeMetric.unavailable(regionalReason);
      }
      if (snapshot.sampledAtMs() == 0L || nowMs < snapshot.sampledAtMs() || nowMs - snapshot.sampledAtMs() > MAX_SNAPSHOT_AGE_MS) {
        return RuntimeMetric.unavailable("stale");
      }
      boolean covered = snapshot.observedPlayers() > 0 || snapshot.onlinePlayers() == 0;
      double value = switch (id) {
        case "region-tps-lowest" -> snapshot.lowestTps();
        case "region-players-below-18-tps" -> snapshot.below18();
        case "region-players-below-15-tps" -> snapshot.below15();
        case "region-player-coverage" -> snapshot.coveragePercent();
        default -> Double.NaN;
      };
      boolean available = Double.isFinite(value) && (id.equals("region-player-coverage") || covered)
          && (!id.equals("region-tps-lowest") || snapshot.observedPlayers() > 0);
      return new RuntimeMetric(Double.isFinite(value) ? value : 0D, snapshot.sampledAtMs(), available, available ? "" : "no-observation");
    }
    if (!jfrReason.isEmpty()) {
      return RuntimeMetric.unavailable(jfrReason);
    }
    JfrTelemetry.Snapshot snapshot = jfrSnapshot;
    if (snapshot.sampledAtMs() == 0L || nowMs < snapshot.sampledAtMs() || nowMs - snapshot.sampledAtMs() > MAX_SNAPSHOT_AGE_MS) {
      return RuntimeMetric.unavailable("stale");
    }
    return snapshot.metrics().getOrDefault(id, RuntimeMetric.unavailable("source-unavailable"));
  }

  private Options configuredOptions() {
    return new Options(regionalEnabled, Math.clamp(playerAnchorsPerRefresh, 1, 512), jfrEnabled, Math.clamp(jfrSlowThresholdMs, 1L, 1_000L));
  }

  private void register(SampleController controller, RuntimeMetricSampler.Definition definition) {
    RuntimeMetricSampler sampler = new RuntimeMetricSampler(definition, () -> metric(definition.id()));
    if (controller.registerSampler(sampler)) {
      registeredIds.add(sampler.getId());
    }
  }

  private void refresh() {
    if (!active) {
      return;
    }
    Options desired = options;
    long revision = configurationRevision.get();
    if (!desired.equals(appliedOptions) || revision != appliedRevision) {
      appliedRevision = revision;
      try {
        reconfigure(desired);
      } catch (RuntimeException | LinkageError failure) {
        regionalReason = "sampling-failed";
        jfrReason = "sampling-failed";
        React.warn("Runtime telemetry configuration failed", failure);
        return;
      }
    }
    long nowMs = System.currentTimeMillis();
    if (pathfinding != null) {
      long nowNanos = System.nanoTime();
      pathfindingSnapshot = pathfinding.snapshot(nowMs, nowNanos - pathfindingAtNanos);
      pathfindingAtNanos = nowNanos;
    }
    if (regionSource != null) {
      try {
        refreshRegions(nowMs, desired.anchors());
      } catch (RuntimeException | LinkageError failure) {
        regionSource = null;
        regionalReason = "sampling-failed";
        React.warn("Folia player-region telemetry stopped after a sampling failure", failure);
      }
    }
    if (jfr != null) {
      try {
        jfrSnapshot = jfr.snapshot(nowMs);
        if (!jfrSnapshot.available()) {
          jfrReason = "sampling-failed";
          closeJfr();
        }
      } catch (RuntimeException failure) {
        jfrReason = "sampling-failed";
        closeJfr();
        React.warn("JFR telemetry stopped after an aggregation failure", failure);
      }
    }
  }

  private void reconfigure(Options desired) {
    appliedOptions = desired;
    regionalHealth.clear();
    regionalSnapshot = RegionalHealth.Snapshot.EMPTY;
    regionSource = null;
    regionalReason = desired.regionalEnabled() ? "source-unavailable" : "disabled";
    if (desired.regionalEnabled() && J.isFoliaThreading()) {
      regionSource = FoliaRegionTps.detect(Bukkit.getServer());
      if (regionSource != null) {
        regionalReason = "warming-up";
      }
    }
    closeJfr();
    jfrSnapshot = JfrTelemetry.Snapshot.EMPTY;
    jfrReason = desired.jfrEnabled() ? "warming-up" : "disabled";
    if (desired.jfrEnabled()) {
      try {
        long generation = jfrGeneration.get();
        JfrTelemetry started = JfrTelemetry.start(new JfrTelemetry.Options(desired.thresholdMs()), failure -> {
          if (active && generation == jfrGeneration.get()) {
            jfrReason = "sampling-failed";
            React.warn("JFR telemetry stream failed", failure);
          }
        });
        synchronized (lifecycleLock) {
          if (active && generation == jfrGeneration.get()) {
            jfr = started;
            jfrReason = "";
          } else {
            started.close();
          }
        }
      } catch (RuntimeException | LinkageError failure) {
        jfrReason = "source-unavailable";
        React.warn("Could not start JFR telemetry", failure);
      }
    }
  }

  private void refreshRegions(long nowMs, int limit) {
    NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
    if (index == null || !index.isInitialSeedReady()) {
      regionalReason = "warming-up";
      return;
    }
    List<NearbyPlayerIndexController.PlayerViewSnapshot> players = index.playerSnapshots();
    List<RegionalHealth.Anchor> anchors = new ArrayList<>(players.size());
    for (NearbyPlayerIndexController.PlayerViewSnapshot player : players) {
      anchors.add(new RegionalHealth.Anchor(player.playerId(), new RegionalHealth.Position(player.worldId(),
          ((int) Math.floor(player.x())) >> 4, ((int) Math.floor(player.z())) >> 4)));
    }
    regionalSnapshot = regionalHealth.sample(anchors, regionSource, limit, nowMs);
    regionalReason = "";
  }

  private void stopExecutor() {
    ScheduledExecutorService current = executor;
    executor = null;
    if (current == null) {
      return;
    }
    current.shutdown();
    try {
      if (!current.awaitTermination(5L, TimeUnit.SECONDS)) {
        current.shutdownNow();
        throw new IllegalStateException("Runtime telemetry refresh did not stop within five seconds");
      }
    } catch (InterruptedException failure) {
      current.shutdownNow();
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while stopping runtime telemetry", failure);
    }
  }

  private void closeJfr() {
    JfrTelemetry previous;
    synchronized (lifecycleLock) {
      jfrGeneration.incrementAndGet();
      previous = jfr;
      jfr = null;
    }
    if (previous != null) {
      previous.close();
    }
  }

  private record Options(boolean regionalEnabled, int anchors, boolean jfrEnabled, long thresholdMs) {
  }
}
