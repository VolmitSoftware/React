package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.ws.CoalescingWsChannel;
import art.arcane.react.core.integration.ThirdPartyMetricRegistry;
import art.arcane.react.core.telemetry.CounterRate;
import art.arcane.react.content.sampler.GCStatsTracker;
import art.arcane.react.core.telemetry.HostTelemetryFeed;
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import art.arcane.react.core.telemetry.PlayerActivityTracker;
import art.arcane.react.core.telemetry.TelemetrySampler;
import art.arcane.react.util.plugin.IController;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

public final class TelemetryController implements IController, Listener {
  private static final long REFRESH_INTERVAL_MS = 1_000L;
  private static final long HISTORY_SIZE_REFRESH_MS = 30_000L;
  private static final long FAILURE_LOG_INTERVAL_MS = 10_000L;

  private final transient PlayerActivityTracker playerActivity;
  private final transient CounterRate historyDropRateTracker;
  private final transient List<String> registeredSamplerIds;
  private transient ScheduledExecutorService refreshExecutor;
  private transient HostTelemetryFeed hostFeed;
  private transient volatile double historyDropsPerMinute;
  private transient volatile long historyDiskBytes;
  private transient volatile long historyWalBytes;
  private transient volatile long historyRateSampledAtMs;
  private transient volatile long historySizeSampledAtMs;
  private transient long nextHistorySizeRefreshMs;
  private transient long lastHistorySizeFailureLogMs;

  public TelemetryController() {
    playerActivity = new PlayerActivityTracker();
    historyDropRateTracker = new CounterRate();
    registeredSamplerIds = new ArrayList<>();
  }

  public static HostTelemetrySnapshot currentHostSnapshot() {
    TelemetryController controller = React.instance == null ? null : React.controller(TelemetryController.class);
    return controller == null ? HostTelemetrySnapshot.empty() : controller.hostSnapshot();
  }

  @Override
  public String getId() {
    return "telemetry";
  }

  @Override
  public String getName() {
    return "Telemetry";
  }

  @Override
  public void start() {
    playerActivity.clear();
    historyDropRateTracker.reset();
    registeredSamplerIds.clear();
    hostFeed = null;
    historyDropsPerMinute = 0D;
    historyDiskBytes = 0L;
    historyWalBytes = 0L;
    historyRateSampledAtMs = 0L;
    historySizeSampledAtMs = 0L;
    nextHistorySizeRefreshMs = 0L;
    lastHistorySizeFailureLogMs = 0L;
  }

  @Override
  public void postStart() {
    long nowMs = System.currentTimeMillis();
    for (Player player : Bukkit.getOnlinePlayers()) {
      playerActivity.recordActive(player.getUniqueId(), nowMs);
    }
    GCStatsTracker.acquire();
    HostTelemetryFeed feed = HostTelemetryFeed.forDataFolder(React.instance.getDataFolder().toPath());
    hostFeed = feed;
    registerSamplers();
    refreshExecutor = Executors.newSingleThreadScheduledExecutor(daemonFactory());
    refreshExecutor.scheduleWithFixedDelay(
        () -> refresh(feed),
        0L,
        REFRESH_INTERVAL_MS,
        TimeUnit.MILLISECONDS
    );
  }

  @Override
  public void stop() {
    ScheduledExecutorService executor = refreshExecutor;
    refreshExecutor = null;
    if (executor != null) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
          React.warn("Timed out waiting for React telemetry refresh to stop.");
        }
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        React.warn("Interrupted while waiting for React telemetry refresh to stop.", failure);
      }
    }
    SampleController sampleController = React.controller(SampleController.class);
    if (sampleController != null) {
      for (String id : List.copyOf(registeredSamplerIds)) {
        sampleController.unregisterSampler(id);
      }
    }
    registeredSamplerIds.clear();
    playerActivity.clear();
    hostFeed = null;
    GCStatsTracker.release();
  }

  public HostTelemetrySnapshot hostSnapshot() {
    HostTelemetryFeed feed = hostFeed;
    return feed == null ? HostTelemetrySnapshot.empty() : feed.snapshot();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(PlayerJoinEvent event) {
    playerActivity.recordJoin(event.getPlayer().getUniqueId(), System.currentTimeMillis());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    playerActivity.recordQuit(event.getPlayer().getUniqueId(), System.currentTimeMillis());
  }

  private void refresh(HostTelemetryFeed feed) {
    long nowMs = System.currentTimeMillis();
    feed.refresh(nowMs);
    refreshHistory(nowMs);
  }

  private void refreshHistory(long nowMs) {
    HistoryController historyController = historyController();
    if (historyController == null) {
      historyDropsPerMinute = 0D;
      historyDiskBytes = 0L;
      historyWalBytes = 0L;
      return;
    }
    historyDropsPerMinute = historyDropRateTracker.perMinute(
        historyController.droppedSnapshotsTotal(),
        nowMs
    );
    historyRateSampledAtMs = nowMs;
    if (nowMs < nextHistorySizeRefreshMs) {
      return;
    }
    nextHistorySizeRefreshMs = nowMs + HISTORY_SIZE_REFRESH_MS;
    try {
      historyDiskBytes = historyController.historyDiskBytes();
      historyWalBytes = historyController.walDiskBytes();
      historySizeSampledAtMs = nowMs;
    } catch (Throwable failure) {
      if (nowMs - lastHistorySizeFailureLogMs >= FAILURE_LOG_INTERVAL_MS) {
        lastHistorySizeFailureLogMs = nowMs;
        React.reportError("Failed to measure React history storage usage", failure);
      }
    }
  }

  private void registerSamplers() {
    SampleController sampleController = React.controller(SampleController.class);
    if (sampleController == null) {
      return;
    }

    register(sampleController, options(
        "react-history-writer-queue", "History Writer Queue", "snapshots", "minecraft:hopper",
        () -> historyLongValue(HistoryController::writerQueueDepth), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-history-writer-capacity", "History Writer Capacity", "snapshots", "minecraft:hopper",
        () -> historyLongValue(HistoryController::writerQueueCapacity), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-history-dropped-snapshots", "History Dropped Snapshots", "total", "minecraft:barrier",
        () -> historyLongValue(HistoryController::droppedSnapshotsTotal), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-history-drop-rate", "History Drop Rate", "/min", "minecraft:barrier",
        () -> historyDropsPerMinute, this::historyAvailable, TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "react-history-persist-lag", "History Persist Lag", "ms", "minecraft:clock",
        () -> historyPersistLag(System.currentTimeMillis()), this::historyAvailable,
        TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "react-history-storage-operational", "History Storage Operational", "", "minecraft:chest",
        this::historyStorageOperational, this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-history-storage-error", "History Storage Error", "", "minecraft:chest",
        this::historyStorageError, this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-history-capture-ms", "History Capture Duration", "ms", "minecraft:clock",
        () -> historyDoubleValue(HistoryController::lastCaptureDurationMs), this::historyAvailable,
        TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "react-history-write-ms", "History Write Duration", "ms", "minecraft:clock",
        () -> historyDoubleValue(HistoryController::lastWriteDurationMs), this::historyAvailable,
        TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "react-history-disk-bytes", "History Disk Usage", "", "minecraft:chest",
        () -> historyDiskBytes, this::historyAvailable, TelemetrySampler.Format.BYTES));
    register(sampleController, options(
        "react-history-wal-bytes", "History WAL Usage", "", "minecraft:writable_book",
        () -> historyWalBytes, this::historyAvailable, TelemetrySampler.Format.BYTES));
    register(sampleController, options(
        "react-samplers-registered", "Registered Samplers", "samplers", "minecraft:comparator",
        () -> historyLongValue(HistoryController::getRegisteredSamplerCount), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-samplers-available", "Available Samplers", "samplers", "minecraft:comparator",
        () -> historyLongValue(HistoryController::getAvailableSamplerCount), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-samplers-unavailable", "Unavailable Samplers", "samplers", "minecraft:comparator",
        () -> historyLongValue(HistoryController::getUnavailableSamplerCount), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-samplers-failed", "Failed Samplers", "samplers", "minecraft:comparator",
        () -> historyLongValue(HistoryController::getFailedSamplerCount), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-published-metrics-accepted", "Published Metrics Accepted", "samples", "minecraft:paper",
        this::publishedAccepted, this::publishedAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-published-metrics-dropped", "Published Metrics Dropped", "samples", "minecraft:paper",
        this::publishedDropped, this::publishedAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-websocket-sessions", "Active WebSocket Sessions", "sessions", "minecraft:compass",
        this::activeWebSocketSessions, this::webAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-websocket-coalesced-frames", "Coalesced WebSocket Frames", "frames", "minecraft:compass",
        CoalescingWsChannel::coalescedFrames, () -> true, TelemetrySampler.Format.COUNT));

    registerHostSamplers(sampleController);
    registerPlayerSamplers(sampleController);
  }

  private void registerHostSamplers(SampleController sampleController) {
    HostTelemetryFeed feed = hostFeed;
    if (feed == null) {
      return;
    }
    for (Sampler sampler : feed.samplers()) {
      if (sampleController.registerSampler(sampler)) {
        registeredSamplerIds.add(sampler.getId());
      }
    }
  }

  private void registerPlayerSamplers(SampleController sampleController) {
    register(sampleController, options(
        "player-joins-rate", "Player Joins", "/min", "minecraft:player_head",
        () -> playerActivity.joinsPerMinute(System.currentTimeMillis()), () -> true,
        TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "player-quits-rate", "Player Quits", "/min", "minecraft:player_head",
        () -> playerActivity.quitsPerMinute(System.currentTimeMillis()), () -> true,
        TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "players-unique-24h", "Unique Players 24h Since Start", "players", "minecraft:player_head",
        () -> playerActivity.uniquePlayers(System.currentTimeMillis()), () -> true,
        TelemetrySampler.Format.COUNT));
  }

  private TelemetrySampler.Options options(
      String id,
      String name,
      String suffix,
      String icon,
      DoubleSupplier valueSupplier,
      BooleanSupplier availabilitySupplier,
      TelemetrySampler.Format format
  ) {
    LongSupplier sampledAt = switch (id) {
      case "react-history-drop-rate" -> () -> historyRateSampledAtMs;
      case "react-history-disk-bytes", "react-history-wal-bytes" -> () -> historySizeSampledAtMs;
      default -> System::currentTimeMillis;
    };
    return new TelemetrySampler.Options(
        id,
        name,
        suffix,
        icon,
        valueSupplier,
        availabilitySupplier,
        format,
        sampledAt
    );
  }

  private void register(SampleController controller, TelemetrySampler.Options options) {
    Sampler sampler = new TelemetrySampler(options);
    if (controller.registerSampler(sampler)) {
      registeredSamplerIds.add(sampler.getId());
    }
  }

  private boolean historyAvailable() {
    return historyController() != null;
  }

  private boolean webAvailable() {
    return React.controller(WebController.class) != null;
  }

  private boolean publishedAvailable() {
    return publishedStore() != null;
  }

  private double activeWebSocketSessions() {
    WebController controller = React.controller(WebController.class);
    return controller == null ? 0D : controller.activeWebSocketSessions();
  }

  private double publishedAccepted() {
    PublishedMetricStore store = publishedStore();
    return store == null ? 0D : store.acceptedSamples();
  }

  private double publishedDropped() {
    PublishedMetricStore store = publishedStore();
    return store == null ? 0D : store.droppedSamples();
  }

  private PublishedMetricStore publishedStore() {
    IntegrationController controller = React.controller(IntegrationController.class);
    ThirdPartyMetricRegistry registry = controller == null ? null : controller.getThirdPartyMetrics();
    return registry == null ? null : registry.store();
  }

  private double historyStorageOperational() {
    HistoryController controller = historyController();
    return controller != null && controller.isStorageOperational() ? 1D : 0D;
  }

  private double historyStorageError() {
    HistoryController controller = historyController();
    return controller != null && controller.getStorageFailure() != null ? 1D : 0D;
  }

  private double historyPersistLag(long nowMs) {
    HistoryController controller = historyController();
    return controller == null ? 0D : controller.persistLagMs(nowMs);
  }

  private double historyLongValue(HistoryLongValue value) {
    HistoryController controller = historyController();
    return controller == null ? 0D : value.read(controller);
  }

  private double historyDoubleValue(HistoryDoubleValue value) {
    HistoryController controller = historyController();
    return controller == null ? 0D : value.read(controller);
  }

  private HistoryController historyController() {
    return React.controller(HistoryController.class);
  }

  private ThreadFactory daemonFactory() {
    return runnable -> {
      Thread thread = new Thread(runnable, "react-host-telemetry");
      thread.setDaemon(true);
      return thread;
    };
  }

  @FunctionalInterface
  private interface HistoryLongValue {
    long read(HistoryController controller);
  }

  @FunctionalInterface
  private interface HistoryDoubleValue {
    double read(HistoryController controller);
  }
}
