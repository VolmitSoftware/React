package art.arcane.react.core.telemetry;

import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.ws.CoalescingWsChannel;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.PlatformLog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * React's own history, sampler, publishing, web socket and player activity samplers. {@link #refresh} runs once a
 * second off the server thread.
 */
public final class SelfTelemetry {
  private static final long HISTORY_SIZE_REFRESH_MS = 30_000L;
  private static final long FAILURE_LOG_INTERVAL_MS = 10_000L;
  private final Sources sources;
  private final PlayerActivityTracker playerActivity = new PlayerActivityTracker();
  private final CounterRate historyDropRateTracker = new CounterRate();
  private final List<String> registeredSamplerIds = new ArrayList<>();
  private volatile double historyDropsPerMinute;
  private volatile long historyDiskBytes;
  private volatile long historyWalBytes;
  private volatile long historyRateSampledAtMs;
  private volatile long historySizeSampledAtMs;
  private long nextHistorySizeRefreshMs;
  private long lastHistorySizeFailureLogMs;

  /** Where the samplers read from. Each source may be absent while its subsystem is not running. */
  public interface Sources {
    HistoryController history();

    boolean webAvailable();

    int activeWebSocketSessions();

    PublishedMetricStore publishedStore();
  }

  public SelfTelemetry(Sources sources) {
    this.sources = sources;
  }

  public void start() {
    playerActivity.clear();
    historyDropRateTracker.reset();
    registeredSamplerIds.clear();
    historyDropsPerMinute = 0D;
    historyDiskBytes = 0L;
    historyWalBytes = 0L;
    historyRateSampledAtMs = 0L;
    historySizeSampledAtMs = 0L;
    nextHistorySizeRefreshMs = 0L;
    lastHistorySizeFailureLogMs = 0L;
  }

  public void stop(SampleController controller) {
    if (controller != null) {
      for (String id : List.copyOf(registeredSamplerIds)) {
        controller.unregisterSampler(id);
      }
    }
    registeredSamplerIds.clear();
    playerActivity.clear();
  }

  public void playerActive(UUID player, long nowMs) {
    playerActivity.recordActive(player, nowMs);
  }

  public void playerJoined(UUID player, long nowMs) {
    playerActivity.recordJoin(player, nowMs);
  }

  public void playerQuit(UUID player, long nowMs) {
    playerActivity.recordQuit(player, nowMs);
  }

  public void refresh(long nowMs) {
    HistoryController historyController = sources.history();
    if (historyController == null) {
      historyDropsPerMinute = 0D;
      historyDiskBytes = 0L;
      historyWalBytes = 0L;
      return;
    }
    historyDropsPerMinute = historyDropRateTracker.perMinute(historyController.droppedSnapshotsTotal(), nowMs);
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
        PlatformLog.reportError("Failed to measure React history storage usage", failure);
      }
    }
  }

  public void registerReactSamplers(SampleController controller) {
    register(controller, options("react-history-writer-queue", "History Writer Queue", "snapshots", "minecraft:hopper",
        () -> historyLongValue(HistoryController::writerQueueDepth), this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-history-writer-capacity", "History Writer Capacity", "snapshots", "minecraft:hopper",
        () -> historyLongValue(HistoryController::writerQueueCapacity), this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-history-dropped-snapshots", "History Dropped Snapshots", "total", "minecraft:barrier",
        () -> historyLongValue(HistoryController::droppedSnapshotsTotal), this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-history-drop-rate", "History Drop Rate", "/min", "minecraft:barrier",
        () -> historyDropsPerMinute, this::historyAvailable, TelemetrySampler.Format.DECIMAL));
    register(controller, options("react-history-persist-lag", "History Persist Lag", "ms", "minecraft:clock",
        () -> historyPersistLag(System.currentTimeMillis()), this::historyAvailable, TelemetrySampler.Format.DECIMAL));
    register(controller, options("react-history-storage-operational", "History Storage Operational", "", "minecraft:chest",
        this::historyStorageOperational, this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-history-storage-error", "History Storage Error", "", "minecraft:chest",
        this::historyStorageError, this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-history-capture-ms", "History Capture Duration", "ms", "minecraft:clock",
        () -> historyDoubleValue(HistoryController::lastCaptureDurationMs), this::historyAvailable, TelemetrySampler.Format.DECIMAL));
    register(controller, options("react-history-write-ms", "History Write Duration", "ms", "minecraft:clock",
        () -> historyDoubleValue(HistoryController::lastWriteDurationMs), this::historyAvailable, TelemetrySampler.Format.DECIMAL));
    register(controller, options("react-history-disk-bytes", "History Disk Usage", "", "minecraft:chest",
        () -> historyDiskBytes, this::historyAvailable, TelemetrySampler.Format.BYTES));
    register(controller, options("react-history-wal-bytes", "History WAL Usage", "", "minecraft:writable_book",
        () -> historyWalBytes, this::historyAvailable, TelemetrySampler.Format.BYTES));
    register(controller, options("react-samplers-registered", "Registered Samplers", "samplers", "minecraft:comparator",
        () -> historyLongValue(HistoryController::getRegisteredSamplerCount), this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-samplers-available", "Available Samplers", "samplers", "minecraft:comparator",
        () -> historyLongValue(HistoryController::getAvailableSamplerCount), this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-samplers-unavailable", "Unavailable Samplers", "samplers", "minecraft:comparator",
        () -> historyLongValue(HistoryController::getUnavailableSamplerCount), this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-samplers-failed", "Failed Samplers", "samplers", "minecraft:comparator",
        () -> historyLongValue(HistoryController::getFailedSamplerCount), this::historyAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-published-metrics-accepted", "Published Metrics Accepted", "samples", "minecraft:paper",
        this::publishedAccepted, this::publishedAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-published-metrics-dropped", "Published Metrics Dropped", "samples", "minecraft:paper",
        this::publishedDropped, this::publishedAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-websocket-sessions", "Active WebSocket Sessions", "sessions", "minecraft:compass",
        () -> sources.activeWebSocketSessions(), sources::webAvailable, TelemetrySampler.Format.COUNT));
    register(controller, options("react-websocket-coalesced-frames", "Coalesced WebSocket Frames", "frames", "minecraft:compass",
        CoalescingWsChannel::coalescedFrames, () -> true, TelemetrySampler.Format.COUNT));
  }

  public void registerPlayerSamplers(SampleController controller) {
    register(controller, options("player-joins-rate", "Player Joins", "/min", "minecraft:player_head",
        () -> playerActivity.joinsPerMinute(System.currentTimeMillis()), () -> true, TelemetrySampler.Format.DECIMAL));
    register(controller, options("player-quits-rate", "Player Quits", "/min", "minecraft:player_head",
        () -> playerActivity.quitsPerMinute(System.currentTimeMillis()), () -> true, TelemetrySampler.Format.DECIMAL));
    register(controller, options("players-unique-24h", "Unique Players 24h Since Start", "players", "minecraft:player_head",
        () -> playerActivity.uniquePlayers(System.currentTimeMillis()), () -> true, TelemetrySampler.Format.COUNT));
  }

  private TelemetrySampler.Options options(String id, String name, String suffix, String icon, DoubleSupplier valueSupplier,
                                           BooleanSupplier availabilitySupplier, TelemetrySampler.Format format) {
    LongSupplier sampledAt = switch (id) {
      case "react-history-drop-rate" -> () -> historyRateSampledAtMs;
      case "react-history-disk-bytes", "react-history-wal-bytes" -> () -> historySizeSampledAtMs;
      default -> System::currentTimeMillis;
    };
    return new TelemetrySampler.Options(id, name, suffix, icon, valueSupplier, availabilitySupplier, format, sampledAt);
  }

  private void register(SampleController controller, TelemetrySampler.Options options) {
    Sampler sampler = new TelemetrySampler(options);
    if (controller.registerSampler(sampler)) {
      registeredSamplerIds.add(sampler.getId());
    }
  }

  private boolean historyAvailable() {
    return sources.history() != null;
  }

  private boolean publishedAvailable() {
    return sources.publishedStore() != null;
  }

  private double publishedAccepted() {
    PublishedMetricStore store = sources.publishedStore();
    return store == null ? 0D : store.acceptedSamples();
  }

  private double publishedDropped() {
    PublishedMetricStore store = sources.publishedStore();
    return store == null ? 0D : store.droppedSamples();
  }

  private double historyStorageOperational() {
    HistoryController controller = sources.history();
    return controller != null && controller.isStorageOperational() ? 1D : 0D;
  }

  private double historyStorageError() {
    HistoryController controller = sources.history();
    return controller != null && controller.getStorageFailure() != null ? 1D : 0D;
  }

  private double historyPersistLag(long nowMs) {
    HistoryController controller = sources.history();
    return controller == null ? 0D : controller.persistLagMs(nowMs);
  }

  private double historyLongValue(HistoryLongValue value) {
    HistoryController controller = sources.history();
    return controller == null ? 0D : value.read(controller);
  }

  private double historyDoubleValue(HistoryDoubleValue value) {
    HistoryController controller = sources.history();
    return controller == null ? 0D : value.read(controller);
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
