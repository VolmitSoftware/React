package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.ws.CoalescingWsChannel;
import art.arcane.react.core.integration.ThirdPartyMetricRegistry;
import art.arcane.react.core.telemetry.CounterRate;
import art.arcane.react.core.telemetry.HostTelemetryProvider;
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import art.arcane.react.core.telemetry.PlayerActivityTracker;
import art.arcane.react.core.telemetry.TelemetrySampler;
import art.arcane.react.util.plugin.IController;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

public final class TelemetryController implements IController, Listener {
  private static final long REFRESH_INTERVAL_MS = 1_000L;
  private static final long HISTORY_SIZE_REFRESH_MS = 30_000L;
  private static final long FAILURE_LOG_INTERVAL_MS = 10_000L;

  private final transient AtomicReference<HostTelemetrySnapshot> hostSnapshot;
  private final transient PlayerActivityTracker playerActivity;
  private final transient CounterRate historyDropRateTracker;
  private final transient List<String> registeredSamplerIds;
  private transient ScheduledExecutorService refreshExecutor;
  private transient HostTelemetryProvider hostProvider;
  private transient volatile double historyDropsPerMinute;
  private transient volatile long historyDiskBytes;
  private transient volatile long historyWalBytes;
  private transient long nextHistorySizeRefreshMs;
  private transient long lastHostFailureLogMs;
  private transient long lastHistorySizeFailureLogMs;

  public TelemetryController() {
    hostSnapshot = new AtomicReference<>(HostTelemetrySnapshot.empty());
    playerActivity = new PlayerActivityTracker();
    historyDropRateTracker = new CounterRate();
    registeredSamplerIds = new ArrayList<>();
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
    hostSnapshot.set(HostTelemetrySnapshot.empty());
    playerActivity.clear();
    historyDropRateTracker.reset();
    registeredSamplerIds.clear();
    hostProvider = null;
    historyDropsPerMinute = 0D;
    historyDiskBytes = 0L;
    historyWalBytes = 0L;
    nextHistorySizeRefreshMs = 0L;
    lastHostFailureLogMs = 0L;
    lastHistorySizeFailureLogMs = 0L;
  }

  @Override
  public void postStart() {
    long nowMs = System.currentTimeMillis();
    for (Player player : Bukkit.getOnlinePlayers()) {
      playerActivity.recordActive(player.getUniqueId(), nowMs);
    }
    registerSamplers();
    Path dataPath = React.instance.getDataFolder().toPath();
    refreshExecutor = Executors.newSingleThreadScheduledExecutor(daemonFactory());
    refreshExecutor.scheduleWithFixedDelay(
        () -> refresh(dataPath),
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
    hostProvider = null;
  }

  public HostTelemetrySnapshot hostSnapshot() {
    return hostSnapshot.get();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(PlayerJoinEvent event) {
    playerActivity.recordJoin(event.getPlayer().getUniqueId(), System.currentTimeMillis());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    playerActivity.recordQuit(event.getPlayer().getUniqueId(), System.currentTimeMillis());
  }

  private void refresh(Path dataPath) {
    long nowMs = System.currentTimeMillis();
    refreshHost(dataPath, nowMs);
    refreshHistory(nowMs);
  }

  private void refreshHost(Path dataPath, long nowMs) {
    try {
      HostTelemetryProvider provider = hostProvider;
      if (provider == null) {
        provider = new HostTelemetryProvider(dataPath);
        hostProvider = provider;
      }
      hostSnapshot.set(provider.capture());
    } catch (Throwable failure) {
      if (nowMs - lastHostFailureLogMs >= FAILURE_LOG_INTERVAL_MS) {
        lastHostFailureLogMs = nowMs;
        React.reportError("Failed to refresh cached React host telemetry", failure);
      }
    }
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
    if (nowMs < nextHistorySizeRefreshMs) {
      return;
    }
    nextHistorySizeRefreshMs = nowMs + HISTORY_SIZE_REFRESH_MS;
    try {
      historyDiskBytes = historyController.historyDiskBytes();
      historyWalBytes = historyController.walDiskBytes();
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
        "react-history-writer-queue", "History Writer Queue", "snapshots", Material.HOPPER,
        () -> historyLongValue(HistoryController::writerQueueDepth), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-history-writer-capacity", "History Writer Capacity", "snapshots", Material.HOPPER,
        () -> historyLongValue(HistoryController::writerQueueCapacity), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-history-dropped-snapshots", "History Dropped Snapshots", "total", Material.BARRIER,
        () -> historyLongValue(HistoryController::droppedSnapshotsTotal), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-history-drop-rate", "History Drop Rate", "/min", Material.BARRIER,
        () -> historyDropsPerMinute, this::historyAvailable, TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "react-history-persist-lag", "History Persist Lag", "ms", Material.CLOCK,
        () -> historyPersistLag(System.currentTimeMillis()), this::historyAvailable,
        TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "react-history-storage-operational", "History Storage Operational", "", Material.CHEST,
        this::historyStorageOperational, this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-history-storage-error", "History Storage Error", "", Material.CHEST,
        this::historyStorageError, this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-history-capture-ms", "History Capture Duration", "ms", Material.CLOCK,
        () -> historyDoubleValue(HistoryController::lastCaptureDurationMs), this::historyAvailable,
        TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "react-history-write-ms", "History Write Duration", "ms", Material.CLOCK,
        () -> historyDoubleValue(HistoryController::lastWriteDurationMs), this::historyAvailable,
        TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "react-history-disk-bytes", "History Disk Usage", "", Material.CHEST,
        () -> historyDiskBytes, this::historyAvailable, TelemetrySampler.Format.BYTES));
    register(sampleController, options(
        "react-history-wal-bytes", "History WAL Usage", "", Material.WRITABLE_BOOK,
        () -> historyWalBytes, this::historyAvailable, TelemetrySampler.Format.BYTES));
    register(sampleController, options(
        "react-samplers-registered", "Registered Samplers", "samplers", Material.COMPARATOR,
        () -> historyLongValue(HistoryController::getRegisteredSamplerCount), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-samplers-available", "Available Samplers", "samplers", Material.COMPARATOR,
        () -> historyLongValue(HistoryController::getAvailableSamplerCount), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-samplers-unavailable", "Unavailable Samplers", "samplers", Material.COMPARATOR,
        () -> historyLongValue(HistoryController::getUnavailableSamplerCount), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-samplers-failed", "Failed Samplers", "samplers", Material.COMPARATOR,
        () -> historyLongValue(HistoryController::getFailedSamplerCount), this::historyAvailable,
        TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-published-metrics-accepted", "Published Metrics Accepted", "samples", Material.PAPER,
        this::publishedAccepted, this::publishedAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-published-metrics-dropped", "Published Metrics Dropped", "samples", Material.PAPER,
        this::publishedDropped, this::publishedAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-websocket-sessions", "Active WebSocket Sessions", "sessions", Material.COMPASS,
        this::activeWebSocketSessions, this::webAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "react-websocket-coalesced-frames", "Coalesced WebSocket Frames", "frames", Material.COMPASS,
        CoalescingWsChannel::coalescedFrames, () -> true, TelemetrySampler.Format.COUNT));

    registerHostSamplers(sampleController);
    registerJvmSamplers(sampleController);
    registerPlayerSamplers(sampleController);
  }

  private void registerHostSamplers(SampleController sampleController) {
    register(sampleController, options(
        "physical-memory-used", "Physical Memory Used", "", Material.WATER_BUCKET,
        () -> hostSnapshot().physicalMemoryUsed(), this::hostAvailable, TelemetrySampler.Format.BYTES));
    register(sampleController, options(
        "physical-memory-free", "Physical Memory Free", "", Material.BUCKET,
        () -> hostSnapshot().physicalMemoryFree(), this::hostAvailable, TelemetrySampler.Format.BYTES));
    register(sampleController, options(
        "disk-usable", "Disk Usable", "", Material.IRON_PICKAXE,
        () -> hostSnapshot().diskUsable(), this::hostAvailable, TelemetrySampler.Format.BYTES));
    register(sampleController, options(
        "disk-read-rate", "Disk Read Rate", "", Material.IRON_PICKAXE,
        () -> hostSnapshot().diskReadBytesPerSecond(), this::hostAvailable,
        TelemetrySampler.Format.BYTES_PER_SECOND));
    register(sampleController, options(
        "disk-write-rate", "Disk Write Rate", "", Material.IRON_PICKAXE,
        () -> hostSnapshot().diskWriteBytesPerSecond(), this::hostAvailable,
        TelemetrySampler.Format.BYTES_PER_SECOND));
    register(sampleController, options(
        "network-receive-rate", "Network Receive Rate", "", Material.OBSERVER,
        () -> hostSnapshot().networkReceiveBytesPerSecond(), this::hostAvailable,
        TelemetrySampler.Format.BYTES_PER_SECOND));
    register(sampleController, options(
        "network-send-rate", "Network Send Rate", "", Material.OBSERVER,
        () -> hostSnapshot().networkSendBytesPerSecond(), this::hostAvailable,
        TelemetrySampler.Format.BYTES_PER_SECOND));
    register(sampleController, options(
        "network-receive-drops", "Network Receive Drops", "drops", Material.OBSERVER,
        () -> hostSnapshot().networkReceiveDrops(), this::hostAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "network-receive-errors", "Network Receive Errors", "errors", Material.OBSERVER,
        () -> hostSnapshot().networkReceiveErrors(), this::hostAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "network-send-errors", "Network Send Errors", "errors", Material.OBSERVER,
        () -> hostSnapshot().networkSendErrors(), this::hostAvailable, TelemetrySampler.Format.COUNT));
  }

  private void registerJvmSamplers(SampleController sampleController) {
    register(sampleController, options(
        "jvm-heap-max", "JVM Heap Max", "", Material.EXPERIENCE_BOTTLE,
        () -> hostSnapshot().heapMax(), this::hostAvailable, TelemetrySampler.Format.BYTES));
    register(sampleController, options(
        "jvm-heap-committed", "JVM Heap Committed", "", Material.EXPERIENCE_BOTTLE,
        () -> hostSnapshot().heapCommitted(), this::hostAvailable, TelemetrySampler.Format.BYTES));
    register(sampleController, options(
        "jvm-heap-utilization", "JVM Heap Utilization", "%", Material.EXPERIENCE_BOTTLE,
        () -> hostSnapshot().heapUtilization(), this::hostAvailable, TelemetrySampler.Format.PERCENT));
    register(sampleController, options(
        "jvm-nonheap-used", "JVM Non-Heap Used", "", Material.EXPERIENCE_BOTTLE,
        () -> hostSnapshot().nonHeapUsed(), this::hostAvailable, TelemetrySampler.Format.BYTES));
    register(sampleController, options(
        "jvm-direct-buffer-bytes", "JVM Direct Buffer Memory", "", Material.EXPERIENCE_BOTTLE,
        () -> hostSnapshot().directBufferBytes(), this::hostAvailable, TelemetrySampler.Format.BYTES));
    register(sampleController, options(
        "jvm-direct-buffer-count", "JVM Direct Buffer Count", "buffers", Material.EXPERIENCE_BOTTLE,
        () -> hostSnapshot().directBufferCount(), this::hostAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "jvm-gc-collections-rate", "JVM GC Collections", "/min", Material.EXPERIENCE_BOTTLE,
        () -> hostSnapshot().gcCollectionsPerMinute(), this::hostAvailable,
        TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "jvm-loaded-classes", "JVM Loaded Classes", "classes", Material.EXPERIENCE_BOTTLE,
        () -> hostSnapshot().loadedClasses(), this::hostAvailable, TelemetrySampler.Format.COUNT));
    register(sampleController, options(
        "jvm-process-uptime", "JVM Process Uptime", "ms", Material.CLOCK,
        () -> hostSnapshot().processUptimeMs(), this::hostAvailable, TelemetrySampler.Format.COUNT));
  }

  private void registerPlayerSamplers(SampleController sampleController) {
    register(sampleController, options(
        "player-joins-rate", "Player Joins", "/min", Material.PLAYER_HEAD,
        () -> playerActivity.joinsPerMinute(System.currentTimeMillis()), () -> true,
        TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "player-quits-rate", "Player Quits", "/min", Material.PLAYER_HEAD,
        () -> playerActivity.quitsPerMinute(System.currentTimeMillis()), () -> true,
        TelemetrySampler.Format.DECIMAL));
    register(sampleController, options(
        "players-unique-24h", "Unique Players 24h Since Start", "players", Material.PLAYER_HEAD,
        () -> playerActivity.uniquePlayers(System.currentTimeMillis()), () -> true,
        TelemetrySampler.Format.COUNT));
  }

  private TelemetrySampler.Options options(
      String id,
      String name,
      String suffix,
      Material icon,
      DoubleSupplier valueSupplier,
      BooleanSupplier availabilitySupplier,
      TelemetrySampler.Format format
  ) {
    return new TelemetrySampler.Options(
        id,
        name,
        suffix,
        icon,
        valueSupplier,
        availabilitySupplier,
        format
    );
  }

  private void register(SampleController controller, TelemetrySampler.Options options) {
    Sampler sampler = new TelemetrySampler(options);
    if (controller.registerSampler(sampler)) {
      registeredSamplerIds.add(sampler.getId());
    }
  }

  private boolean hostAvailable() {
    return hostSnapshot().available();
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
