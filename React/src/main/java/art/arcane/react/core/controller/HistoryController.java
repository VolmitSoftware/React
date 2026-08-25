package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.history.HistoryPoint;
import art.arcane.react.core.history.HistoryQueryEngine;
import art.arcane.react.core.history.HistoryQueryResult;
import art.arcane.react.core.history.HistoryQuerySeries;
import art.arcane.react.core.history.HistorySegment;
import art.arcane.react.core.history.HistorySeries;
import art.arcane.react.core.history.HistoryStore;
import art.arcane.react.core.history.HistoryTier;
import art.arcane.react.core.history.HistoryWal;
import art.arcane.react.core.history.MetricDescriptor;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.react.util.project.registry.Registry;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Data
@ConfigDescription("Captures React samplers once, stores compressed historical telemetry, and serves bounded time-range queries.")
public class HistoryController implements IController {
  private static final long CAPTURE_DRIVER_INTERVAL_MS = 100L;
  private static final long FAILURE_LOG_INTERVAL_MS = 10_000L;
  private static final int WRITER_QUEUE_CAPACITY = 32;

  @ConfigDoc(value = "Persists sampler history under plugins/React/history.", impact = "Disabling storage keeps live web snapshots but records no new historical samples. Existing history remains readable.")
  private boolean enabled = true;
  @ConfigDoc(value = "Cadence for the authoritative live sampler snapshot in milliseconds.", impact = "Lower values improve live chart fidelity but poll every registered sampler more often.")
  private long liveCaptureIntervalMs = 500L;
  @ConfigDoc(value = "Maximum time between forced history journal writes in milliseconds.", impact = "A process or host crash can lose at most this recent window; lower values increase forced disk writes.")
  private long walForceIntervalMs = 5_000L;
  @ConfigDoc(value = "DEFLATE level used only when it makes an encoded metric column smaller.", impact = "Higher values may reduce disk use at additional writer CPU cost.")
  private int compressionLevel = 3;
  @ConfigDoc(value = "Retention for exact one-second samples in hours. Zero retains this tier indefinitely.", impact = "Older coverage remains available through lower-resolution rollups.")
  private long rawRetentionHours = 48L;
  @ConfigDoc(value = "Retention for ten-second rollups in days. Zero retains this tier indefinitely.", impact = "Older coverage remains available through lower-resolution rollups.")
  private long tenSecondRetentionDays = 14L;
  @ConfigDoc(value = "Retention for one-minute rollups in days. Zero retains this tier indefinitely.", impact = "Older coverage remains available through lower-resolution rollups.")
  private long minuteRetentionDays = 180L;
  @ConfigDoc(value = "Retention for fifteen-minute rollups in days. Zero retains this tier indefinitely.", impact = "One-hour rollups retain historical coverage after this tier expires.")
  private long fifteenMinuteRetentionDays = 730L;
  @ConfigDoc(value = "Maximum series accepted by one historical query.", impact = "Higher values can create larger relay frames and more concurrent disk reads.")
  private int maxQuerySeries = 16;
  @ConfigDoc(value = "Maximum buckets accepted for each series across a historical query.", impact = "Higher values improve chart resolution at the cost of response size and query work.")
  private int maxQueryPoints = 4_096;
  @ConfigDoc(value = "Maximum buckets returned per series in one historical response page.", impact = "Pages stay bounded for the relay and browser request timeouts.")
  private int queryPagePoints = 256;

  private transient AtomicReference<MetricSnapshot> latestSnapshot;
  private transient AtomicLong sequence;
  private transient AtomicLong droppedSnapshots;
  private transient ScheduledExecutorService captureExecutor;
  private transient ThreadPoolExecutor writerExecutor;
  private transient HistoryStore historyStore;
  private transient HistoryWal wal;
  private transient HistoryQueryEngine queryEngine;
  private transient HistorySegment activeSegment;
  private transient Object activeSegmentLock;
  private transient Map<String, Long> samplerFailureLogMs;
  private transient volatile boolean storageOperational;
  private transient volatile boolean stopping;
  private transient volatile Throwable storageFailure;
  private transient long lastCaptureNanos;
  private transient long lastPersistedBucket;
  private transient long lastWalForceMs;
  private transient long lastWriterFailureLogMs;
  private transient long lastQueueFailureLogMs;

  public HistoryController() {
    latestSnapshot = new AtomicReference<>(MetricSnapshot.empty());
    sequence = new AtomicLong();
    droppedSnapshots = new AtomicLong();
    activeSegmentLock = new Object();
    samplerFailureLogMs = new HashMap<>();
    lastPersistedBucket = Long.MIN_VALUE;
  }

  @Override
  public String getId() {
    return "history";
  }

  @Override
  public String getName() {
    return "History";
  }

  @Override
  public void start() {
    latestSnapshot.set(MetricSnapshot.empty());
    sequence.set(0L);
    droppedSnapshots.set(0L);
    samplerFailureLogMs.clear();
    storageOperational = false;
    stopping = false;
    storageFailure = null;
    activeSegment = null;
    lastCaptureNanos = 0L;
    lastPersistedBucket = Long.MIN_VALUE;
    lastWalForceMs = 0L;
    lastWriterFailureLogMs = 0L;
    lastQueueFailureLogMs = 0L;
  }

  @Override
  public void postStart() {
    writerExecutor = createWriterExecutor();
    captureExecutor = Executors.newSingleThreadScheduledExecutor(daemonFactory("react-history-capture"));
    writerExecutor.execute(this::initializeStorage);
    captureExecutor.scheduleAtFixedRate(
        this::captureIfDue,
        0L,
        CAPTURE_DRIVER_INTERVAL_MS,
        TimeUnit.MILLISECONDS
    );
  }

  @Override
  public void stop() {
    stopping = true;
    ScheduledExecutorService localCapture = captureExecutor;
    captureExecutor = null;
    if (localCapture != null) {
      localCapture.shutdown();
      await(localCapture, 5L, "history capture");
    }

    ThreadPoolExecutor localWriter = writerExecutor;
    writerExecutor = null;
    if (localWriter != null) {
      try {
        localWriter.execute(this::closeStorage);
      } catch (RejectedExecutionException failure) {
        React.warn("React history writer rejected its shutdown flush", failure);
      }
      localWriter.shutdown();
      await(localWriter, 30L, "history writer");
    } else {
      closeStorage();
    }
    storageOperational = false;
  }

  public MetricSnapshot latest() {
    return latestSnapshot.get();
  }

  public List<MetricDescriptor> descriptors() {
    HistoryStore store = historyStore;
    if (store != null) {
      return store.descriptors();
    }
    List<MetricDescriptor> descriptors = new ArrayList<>();
    MetricSnapshot snapshot = latest();
    for (MetricSnapshotValue value : snapshot.values()) {
      descriptors.add(new MetricDescriptor(
          value.id(),
          value.name(),
          value.suffix(),
          0L,
          0L,
          true
      ));
    }
    return List.copyOf(descriptors);
  }

  public boolean knowsMetric(String id) {
    if (id == null || id.isBlank()) {
      return false;
    }
    if (latest().value(id) != null) {
      return true;
    }
    HistoryStore store = historyStore;
    return store != null && store.descriptor(id) != null;
  }

  public long selectResolution(long fromMs, long toMs, int requestedMaxPoints) {
    HistoryQueryEngine engine = queryEngine;
    if (engine == null) {
      return HistoryTier.ONE_HOUR.intervalMs();
    }
    return engine.selectResolution(
        fromMs,
        toMs,
        Math.max(1, Math.min(requestedMaxPoints, effectiveMaxQueryPoints())),
        System.currentTimeMillis(),
        retentionByTier()
    );
  }

  public HistoryQueryResult query(
      List<String> ids,
      long fromMs,
      long toMs,
      long resolutionMs,
      long throughSequence,
      long throughMs
  ) throws IOException {
    HistoryQueryEngine engine = queryEngine;
    if (engine == null) {
      List<HistoryQuerySeries> empty = ids.stream()
          .map(id -> new HistoryQuerySeries(id, id, "", List.of()))
          .toList();
      return new HistoryQueryResult(fromMs, toMs, resolutionMs, throughSequence, throughMs, empty);
    }
    return engine.query(ids, fromMs, toMs, resolutionMs, throughSequence, throughMs);
  }

  public int effectiveMaxQuerySeries() {
    return Math.max(1, Math.min(64, maxQuerySeries));
  }

  public int effectiveMaxQueryPoints() {
    return Math.max(64, Math.min(16_384, maxQueryPoints));
  }

  public int effectiveQueryPagePoints() {
    return Math.max(32, Math.min(512, queryPagePoints));
  }

  public long historyDiskBytes() throws IOException {
    HistoryStore store = historyStore;
    return store == null ? 0L : store.diskBytes();
  }

  private void initializeStorage() {
    File root = React.instance.getDataFile("history");
    HistoryStore store = new HistoryStore(root.toPath(), effectiveCompressionLevel());
    try {
      store.initialize();
      historyStore = store;
      queryEngine = new HistoryQueryEngine(store, this::activePoints);
      if (enabled) {
        HistoryWal localWal = new HistoryWal(store.walPath());
        HistoryWal.WalRecovery recovery = localWal.open();
        wal = localWal;
        recover(recovery);
        store.compactAll(System.currentTimeMillis());
        store.prune(System.currentTimeMillis(), retentionByTier());
        storageOperational = true;
        if (recovery.truncated()) {
          React.warn("Recovered React history through the last valid journal frame after an incomplete or corrupt tail.");
        }
      }
    } catch (Throwable failure) {
      storageFailure = failure;
      storageOperational = false;
      React.reportError("Failed to initialize React metric history storage", failure);
      closeWal();
    }
  }

  private void recover(HistoryWal.WalRecovery recovery) throws IOException {
    long nowMs = System.currentTimeMillis();
    long currentStart = HistoryTier.RAW.segmentStart(nowMs);
    Map<Long, HistorySegment> recovered = new LinkedHashMap<>();
    HistoryStore store = historyStore;
    if (store != null && store.contains(HistoryTier.RAW, currentStart)) {
      HistorySegment existing = store.read(HistoryTier.RAW, currentStart, null);
      if (existing != null) {
        recovered.put(currentStart, existing);
      }
    }
    for (HistoryWal.WalSample sample : recovery.samples()) {
      sequence.accumulateAndGet(sample.sequence(), Math::max);
      long bucketTimestamp = rawBucket(sample.timestampMs());
      long segmentStart = HistoryTier.RAW.segmentStart(bucketTimestamp);
      HistorySegment segment = recovered.get(segmentStart);
      if (segment == null) {
        if (store != null && store.contains(HistoryTier.RAW, segmentStart)) {
          segment = store.read(HistoryTier.RAW, segmentStart, null);
        }
        if (segment == null) {
          segment = newRawSegment(segmentStart);
        }
        recovered.put(segmentStart, segment);
      }
      int bucketIndex = rawBucketIndex(segmentStart, bucketTimestamp);
      for (HistoryWal.WalValue value : sample.values()) {
        HistoryWal.WalMetric metric = recovery.metrics().get(value.index());
        if (metric == null) {
          continue;
        }
        segment.series(metric.id(), metric.name(), metric.suffix()).set(bucketIndex, value.value());
      }
    }
    for (Map.Entry<Long, HistorySegment> entry : recovered.entrySet()) {
      if (entry.getKey() == currentStart) {
        synchronized (activeSegmentLock) {
          activeSegment = entry.getValue();
        }
      }
      if (!entry.getValue().series().isEmpty() && store != null) {
        store.write(entry.getValue());
      }
    }
    HistoryWal localWal = wal;
    if (localWal != null) {
      localWal.reset();
    }
  }

  private void captureIfDue() {
    if (stopping) {
      return;
    }
    long nowNanos = System.nanoTime();
    long intervalNanos = TimeUnit.MILLISECONDS.toNanos(effectiveLiveCaptureIntervalMs());
    if (lastCaptureNanos != 0L && nowNanos - lastCaptureNanos < intervalNanos) {
      return;
    }
    lastCaptureNanos = nowNanos;
    try {
      MetricSnapshot snapshot = captureSnapshot();
      latestSnapshot.set(snapshot);
      HistoryStore store = historyStore;
      if (store != null) {
        store.updateLiveDescriptors(snapshot);
      }
      long bucket = rawBucket(snapshot.capturedAtMs());
      if (enabled && storageOperational && bucket != lastPersistedBucket) {
        lastPersistedBucket = bucket;
        enqueue(snapshot);
      }
    } catch (Throwable failure) {
      React.reportError("React metric history capture failed", failure);
    }
  }

  private MetricSnapshot captureSnapshot() {
    SampleController sampleController = React.controller(SampleController.class);
    Registry<Sampler> registry = sampleController == null ? null : sampleController.getSamplers();
    long capturedAtMs = System.currentTimeMillis();
    long currentSequence = sequence.incrementAndGet();
    if (registry == null) {
      return MetricSnapshot.of(currentSequence, capturedAtMs, List.of());
    }
    List<Sampler> samplers = new ArrayList<>(registry.all());
    samplers.sort(Comparator.comparing(Sampler::getId));
    List<MetricSnapshotValue> values = new ArrayList<>(samplers.size());
    for (Sampler sampler : samplers) {
      try {
        double value = sampler.sample();
        boolean available = sampler.isSampleAvailable() && Double.isFinite(value);
        String suffix = sampler.formattedSuffix(value);
        String display = sampler.formattedValue(value);
        values.add(new MetricSnapshotValue(
            sampler.getId(),
            sampler.getName(),
            suffix == null ? "" : suffix,
            Double.isFinite(value) ? value : 0D,
            display == null ? "" : display,
            available
        ));
      } catch (Throwable failure) {
        recordSamplerFailure(sampler, failure, capturedAtMs);
        values.add(new MetricSnapshotValue(
            sampler.getId(),
            sampler.getName(),
            "",
            0D,
            "",
            false
        ));
      }
    }
    return MetricSnapshot.of(currentSequence, capturedAtMs, values);
  }

  private void enqueue(MetricSnapshot snapshot) {
    ThreadPoolExecutor writer = writerExecutor;
    if (writer == null || writer.isShutdown()) {
      droppedSnapshots.incrementAndGet();
      return;
    }
    try {
      writer.execute(() -> persistSafely(snapshot));
    } catch (RejectedExecutionException failure) {
      droppedSnapshots.incrementAndGet();
      long nowMs = System.currentTimeMillis();
      if (nowMs - lastQueueFailureLogMs >= FAILURE_LOG_INTERVAL_MS) {
        lastQueueFailureLogMs = nowMs;
        React.warn("React history writer queue is saturated; the affected sample is recorded as a gap.", failure);
      }
    }
  }

  private void persistSafely(MetricSnapshot snapshot) {
    try {
      persist(snapshot);
    } catch (Throwable failure) {
      storageFailure = failure;
      long nowMs = System.currentTimeMillis();
      if (nowMs - lastWriterFailureLogMs >= FAILURE_LOG_INTERVAL_MS) {
        lastWriterFailureLogMs = nowMs;
        React.reportError("Failed to persist React metric history", failure);
      }
    }
  }

  private void persist(MetricSnapshot snapshot) throws IOException {
    HistoryStore store = historyStore;
    HistoryWal localWal = wal;
    if (store == null || localWal == null) {
      return;
    }
    store.compressionLevel(effectiveCompressionLevel());
    long bucketTimestamp = rawBucket(snapshot.capturedAtMs());
    long segmentStart = HistoryTier.RAW.segmentStart(bucketTimestamp);
    synchronized (activeSegmentLock) {
      if (activeSegment == null || activeSegment.startMs() != segmentStart) {
        sealActive(store);
        localWal.reset();
        activeSegment = store.contains(HistoryTier.RAW, segmentStart)
            ? store.read(HistoryTier.RAW, segmentStart, null)
            : newRawSegment(segmentStart);
        store.compactAll(bucketTimestamp);
        store.prune(bucketTimestamp, retentionByTier());
      }
      localWal.append(snapshot);
      int bucketIndex = rawBucketIndex(segmentStart, bucketTimestamp);
      for (MetricSnapshotValue value : snapshot.values()) {
        if (value.available() && Double.isFinite(value.value())) {
          activeSegment.series(value.id(), value.name(), value.suffix()).set(bucketIndex, value.value());
        }
      }
    }
    if (snapshot.capturedAtMs() - lastWalForceMs >= effectiveWalForceIntervalMs()) {
      localWal.force();
      lastWalForceMs = snapshot.capturedAtMs();
    }
  }

  private Map<String, List<HistoryPoint>> activePoints(Set<String> ids, long fromMs, long toMs) {
    Map<String, List<HistoryPoint>> points = new HashMap<>();
    synchronized (activeSegmentLock) {
      HistorySegment segment = activeSegment;
      if (segment == null) {
        return points;
      }
      int fromIndex = (int) Math.max(0L, Math.floorDiv(fromMs - segment.startMs(), HistoryTier.RAW.intervalMs()));
      int toIndex = (int) Math.min(
          segment.bucketCount(),
          Math.floorDiv(toMs - 1L - segment.startMs(), HistoryTier.RAW.intervalMs()) + 1L
      );
      for (String id : ids) {
        HistorySeries series = segment.series(id);
        if (series == null) {
          continue;
        }
        List<HistoryPoint> seriesPoints = new ArrayList<>();
        for (int index = fromIndex; index < toIndex; index++) {
          if (series.count(index) > 0L) {
            seriesPoints.add(series.point(segment.startMs(), HistoryTier.RAW.intervalMs(), index));
          }
        }
        if (!seriesPoints.isEmpty()) {
          points.put(id, seriesPoints);
        }
      }
    }
    return points;
  }

  private void closeStorage() {
    HistoryStore store = historyStore;
    try {
      HistoryWal localWal = wal;
      if (localWal != null) {
        localWal.force();
      }
      synchronized (activeSegmentLock) {
        if (store != null) {
          sealActive(store);
        }
        if (localWal != null) {
          localWal.reset();
        }
      }
    } catch (Throwable failure) {
      React.reportError("Failed to flush React metric history during shutdown", failure);
    } finally {
      closeWal();
    }
  }

  private void closeWal() {
    HistoryWal localWal = wal;
    wal = null;
    if (localWal == null) {
      return;
    }
    try {
      localWal.close();
    } catch (Throwable failure) {
      React.reportError("Failed to close React metric history journal", failure);
    }
  }

  private void sealActive(HistoryStore store) throws IOException {
    if (activeSegment == null || activeSegment.series().isEmpty()) {
      activeSegment = null;
      return;
    }
    store.write(activeSegment);
    activeSegment = null;
  }

  private HistorySegment newRawSegment(long startMs) {
    int buckets = Math.toIntExact(HistoryTier.RAW.segmentDurationMs() / HistoryTier.RAW.intervalMs());
    return new HistorySegment(HistoryTier.RAW, startMs, buckets);
  }

  private Map<HistoryTier, Long> retentionByTier() {
    EnumMap<HistoryTier, Long> retention = new EnumMap<>(HistoryTier.class);
    retention.put(HistoryTier.RAW, hours(rawRetentionHours));
    retention.put(HistoryTier.TEN_SECONDS, days(tenSecondRetentionDays));
    retention.put(HistoryTier.ONE_MINUTE, days(minuteRetentionDays));
    retention.put(HistoryTier.FIFTEEN_MINUTES, days(fifteenMinuteRetentionDays));
    retention.put(HistoryTier.ONE_HOUR, 0L);
    return retention;
  }

  private void recordSamplerFailure(Sampler sampler, Throwable failure, long nowMs) {
    String id = sampler == null || sampler.getId() == null ? "unknown" : sampler.getId();
    long last = samplerFailureLogMs.getOrDefault(id, 0L);
    if (nowMs - last < FAILURE_LOG_INTERVAL_MS) {
      return;
    }
    samplerFailureLogMs.put(id, nowMs);
    React.reportError("Sampler " + id + " failed during the authoritative history snapshot", failure);
  }

  private ThreadPoolExecutor createWriterExecutor() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(WRITER_QUEUE_CAPACITY),
        daemonFactory("react-history-writer"),
        new ThreadPoolExecutor.AbortPolicy()
    );
    executor.prestartAllCoreThreads();
    return executor;
  }

  private ThreadFactory daemonFactory(String name) {
    return runnable -> {
      Thread thread = new Thread(runnable, name);
      thread.setDaemon(true);
      return thread;
    };
  }

  private void await(ExecutorService executor, long timeoutSeconds, String label) {
    try {
      if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
        React.warn("Timed out waiting for React " + label + " to stop.");
      }
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      React.warn("Interrupted while waiting for React " + label + " to stop.", failure);
    }
  }

  private long effectiveLiveCaptureIntervalMs() {
    return Math.max(250L, Math.min(10_000L, liveCaptureIntervalMs));
  }

  private long effectiveWalForceIntervalMs() {
    return Math.max(1_000L, Math.min(60_000L, walForceIntervalMs));
  }

  private int effectiveCompressionLevel() {
    return Math.max(0, Math.min(9, compressionLevel));
  }

  private static long rawBucket(long timestampMs) {
    return Math.floorDiv(timestampMs, HistoryTier.RAW.intervalMs()) * HistoryTier.RAW.intervalMs();
  }

  private static int rawBucketIndex(long segmentStart, long bucketTimestamp) {
    return Math.toIntExact((bucketTimestamp - segmentStart) / HistoryTier.RAW.intervalMs());
  }

  private static long hours(long value) {
    return value <= 0L ? 0L : TimeUnit.HOURS.toMillis(value);
  }

  private static long days(long value) {
    return value <= 0L ? 0L : TimeUnit.DAYS.toMillis(value);
  }
}
