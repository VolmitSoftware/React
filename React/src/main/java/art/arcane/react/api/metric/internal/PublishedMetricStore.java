package art.arcane.react.api.metric.internal;

import art.arcane.react.api.metric.ReactMetric;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PublishedMetricStore implements MetricBinding {
  public static final long MAX_STALE_MS = 15_000L;
  public static final long MAX_FUTURE_MS = 5_000L;
  public static final int MAX_METRICS_PER_SOURCE = 24;
  public static final int MAX_SOURCES = 16;

  private final Map<String, List<ReactMetric>> declaredBySource = new ConcurrentHashMap<>();
  private final Map<String, ReactMetric> metricsByKey = new ConcurrentHashMap<>();
  private final Map<String, Reading> readings = new ConcurrentHashMap<>();
  private final AtomicLong accepted = new AtomicLong();
  private final AtomicLong dropped = new AtomicLong();

  public List<ReactMetric> declare(String sourceId, List<ReactMetric> metrics) {
    String source = MetricKeys.normalizeSourceId(sourceId);

    if (!MetricKeys.isAcceptableSourceId(source)) {
      return List.of();
    }

    if (!declaredBySource.containsKey(source) && declaredBySource.size() >= MAX_SOURCES) {
      return List.of();
    }

    List<ReactMetric> sanitized = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();

    if (metrics != null) {
      for (ReactMetric metric : metrics) {
        if (sanitized.size() >= MAX_METRICS_PER_SOURCE) {
          break;
        }

        if (metric == null) {
          continue;
        }

        String key = metric.key() == null ? "" : metric.key().strip();

        if (!MetricKeys.isValidKey(source, key) || !seen.add(key)) {
          continue;
        }

        sanitized.add(new ReactMetric(
            key,
            metric.kind(),
            MetricText.sanitize(metric.unit(), 16),
            MetricText.sanitize(metric.displayName(), 48),
            metric.icon(),
            metric.decimals()));
      }
    }

    List<ReactMetric> immutable = List.copyOf(sanitized);
    List<ReactMetric> previous = declaredBySource.put(source, immutable);

    if (previous != null) {
      for (ReactMetric metric : previous) {
        if (!seen.contains(metric.key())) {
          metricsByKey.remove(metric.key());
          readings.remove(metric.key());
        }
      }
    }

    for (ReactMetric metric : immutable) {
      metricsByKey.put(metric.key(), metric);
    }

    return immutable;
  }

  public List<ReactMetric> declared(String sourceId) {
    List<ReactMetric> metrics = declaredBySource.get(MetricKeys.normalizeSourceId(sourceId));
    return metrics == null ? List.of() : metrics;
  }

  public void withdrawSource(String sourceId) {
    List<ReactMetric> previous = declaredBySource.remove(MetricKeys.normalizeSourceId(sourceId));

    if (previous == null) {
      return;
    }

    for (ReactMetric metric : previous) {
      metricsByKey.remove(metric.key());
      readings.remove(metric.key());
    }
  }

  @Override
  public boolean accepting(String sourceId) {
    return declaredBySource.containsKey(MetricKeys.normalizeSourceId(sourceId));
  }

  @Override
  public boolean publish(String sourceId, String key, double value, long sampledAtMs, long nowMs) {
    String source = MetricKeys.normalizeSourceId(sourceId);
    ReactMetric metric = key == null ? null : metricsByKey.get(key);

    if (metric == null
        || !declaredBySource.containsKey(source)
        || !MetricKeys.isValidKey(source, key)
        || !Double.isFinite(value)
        || sampledAtMs <= 0L
        || sampledAtMs > nowMs + MAX_FUTURE_MS
        || nowMs - sampledAtMs > MAX_STALE_MS) {
      dropped.incrementAndGet();
      return false;
    }

    readings.put(key, new Reading(value, sampledAtMs));
    accepted.incrementAndGet();
    return true;
  }

  @Override
  public void withdraw(String sourceId, String key) {
    String source = MetricKeys.normalizeSourceId(sourceId);

    if (key != null && MetricKeys.isValidKey(source, key)) {
      readings.remove(key);
    }
  }

  public boolean available(String key, long nowMs) {
    Reading reading = key == null ? null : readings.get(key);
    return reading != null && nowMs - reading.sampledAtMs() <= MAX_STALE_MS;
  }

  public double valueOr(String key, double fallback, long nowMs) {
    Reading reading = key == null ? null : readings.get(key);

    if (reading == null || nowMs - reading.sampledAtMs() > MAX_STALE_MS) {
      return fallback;
    }

    return reading.value();
  }

  public ReactMetric metric(String key) {
    return key == null ? null : metricsByKey.get(key);
  }

  @Override
  public Set<String> sourceIds() {
    return Set.copyOf(declaredBySource.keySet());
  }

  public Set<String> keys() {
    return Set.copyOf(metricsByKey.keySet());
  }

  public long acceptedSamples() {
    return accepted.get();
  }

  public long droppedSamples() {
    return dropped.get();
  }

  public void clear() {
    declaredBySource.clear();
    metricsByKey.clear();
    readings.clear();
  }

  private record Reading(double value, long sampledAtMs) {
  }
}
