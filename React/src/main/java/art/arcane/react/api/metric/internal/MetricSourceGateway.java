package art.arcane.react.api.metric.internal;

import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.ReactMetricSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class MetricSourceGateway {
  public static final int DEFAULT_FAULT_LIMIT = 5;
  public static final long DEFAULT_SLOW_SOURCE_MILLIS = 5L;
  public static final int MAX_METRICS_PER_SOURCE = 64;

  private final int faultLimit;
  private final long slowSourceMillis;
  private final Consumer<String> log;
  private final Map<String, Integer> faults = new ConcurrentHashMap<>();
  private final Set<String> quarantined = ConcurrentHashMap.newKeySet();
  private final Set<String> slowWarned = ConcurrentHashMap.newKeySet();
  private final AtomicLong totalFaults = new AtomicLong();
  private final AtomicLong totalRefusals = new AtomicLong();

  public MetricSourceGateway(int faultLimit, long slowSourceMillis, Consumer<String> log) {
    this.faultLimit = Math.max(0, faultLimit);
    this.slowSourceMillis = Math.max(0L, slowSourceMillis);
    this.log = log == null ? message -> {
    } : log;
  }

  public String sourceIdOf(ReactMetricSource source, String pluginName) {
    if (source == null) {
      return "";
    }

    String owner = pluginName == null || pluginName.isBlank() ? "unknown" : pluginName;
    String sourceId;

    try {
      sourceId = MetricKeys.normalizeSourceId(source.sourceId());
    } catch (Throwable e) {
      totalRefusals.incrementAndGet();
      log.accept("[metric] source from " + owner + " refused: sourceId() threw "
          + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage()));
      return "";
    }

    if (!MetricKeys.isValidSourceId(sourceId)) {
      totalRefusals.incrementAndGet();
      log.accept("[metric] source from " + owner + " refused: sourceId must be 2-"
          + MetricKeys.MAX_SOURCE_ID_LENGTH + " lowercase characters of [a-z0-9_-]");
      return "";
    }

    if (MetricKeys.isReserved(sourceId)) {
      totalRefusals.incrementAndGet();
      log.accept("[metric] source from " + owner + " refused: sourceId '" + sourceId + "' is reserved");
      return "";
    }

    return sourceId;
  }

  public List<ReactMetric> declarationOf(ReactMetricSource source, String sourceId, String pluginName) {
    if (source == null || sourceId == null || sourceId.isEmpty() || quarantined.contains(sourceId)) {
      return null;
    }

    String owner = pluginName == null || pluginName.isBlank() ? "unknown" : pluginName;
    List<ReactMetric> collected = new ArrayList<>();
    boolean returnedNull = false;
    boolean overflowed = false;
    long startedAt = System.nanoTime();

    try {
      List<ReactMetric> metrics = source.metrics();

      if (metrics == null) {
        returnedNull = true;
      } else {
        for (ReactMetric metric : metrics) {
          if (metric == null) {
            continue;
          }

          if (collected.size() >= MAX_METRICS_PER_SOURCE) {
            overflowed = true;
            break;
          }

          collected.add(metric);
        }
      }
    } catch (Throwable e) {
      recordFault(sourceId, owner, "metrics() threw " + e.getClass().getSimpleName()
          + (e.getMessage() == null ? "" : " - " + e.getMessage()));
      return null;
    }

    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

    if (slowSourceMillis > 0L && elapsedMillis >= slowSourceMillis && slowWarned.add(sourceId)) {
      log.accept("[metric] source " + sourceId + " from " + owner + " took " + elapsedMillis
          + "ms in metrics(); this runs on the server thread, do not block");
    }

    if (returnedNull) {
      recordFault(sourceId, owner, "metrics() returned null");
      return null;
    }

    if (overflowed) {
      log.accept("[metric] source " + sourceId + " from " + owner + " declared more than "
          + MAX_METRICS_PER_SOURCE + " metrics; the remainder is ignored");
    }

    return List.copyOf(collected);
  }

  public boolean isQuarantined(String sourceId) {
    return sourceId != null && quarantined.contains(sourceId);
  }

  public int faultCount(String sourceId) {
    return sourceId == null ? 0 : faults.getOrDefault(sourceId, 0);
  }

  public long totalFaults() {
    return totalFaults.get();
  }

  public long totalRefusals() {
    return totalRefusals.get();
  }

  public void forget(String sourceId) {
    if (sourceId == null) {
      return;
    }

    faults.remove(sourceId);
    quarantined.remove(sourceId);
    slowWarned.remove(sourceId);
  }

  private void recordFault(String sourceId, String pluginName, String detail) {
    totalFaults.incrementAndGet();
    int count = faults.merge(sourceId, 1, Integer::sum);
    log.accept("[metric] source " + sourceId + " from " + pluginName + " faulted (" + count + "): " + detail);

    if (faultLimit > 0 && count >= faultLimit && quarantined.add(sourceId)) {
      log.accept("[metric] source " + sourceId + " from " + pluginName + " quarantined after "
          + count + " faults; it is skipped until it re-registers");
    }
  }
}
