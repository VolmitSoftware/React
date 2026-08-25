package art.arcane.react.core.history;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HistoryQueryEngine {
  private final HistoryStore store;
  private final ActivePointSource activePointSource;

  public HistoryQueryEngine(HistoryStore store, ActivePointSource activePointSource) {
    this.store = store;
    this.activePointSource = activePointSource;
  }

  public long selectResolution(
      long fromMs,
      long toMs,
      int maxPoints,
      long nowMs,
      Map<HistoryTier, Long> retentionByTier
  ) {
    long range = Math.max(1L, toMs - fromMs);
    long viewportResolution = ceilDiv(range, Math.max(1, maxPoints));
    long ageResolution = HistoryTier.ONE_HOUR.intervalMs();
    for (HistoryTier tier : HistoryTier.values()) {
      long retention = retentionByTier.getOrDefault(tier, 0L);
      if (retention <= 0L || fromMs >= nowMs - retention) {
        ageResolution = tier.intervalMs();
        break;
      }
    }
    long required = Math.max(viewportResolution, ageResolution);
    for (HistoryTier tier : HistoryTier.values()) {
      if (required <= tier.intervalMs()) {
        return tier.intervalMs();
      }
    }
    return ceilDiv(required, HistoryTier.ONE_HOUR.intervalMs()) * HistoryTier.ONE_HOUR.intervalMs();
  }

  public HistoryQueryResult query(
      List<String> requestedIds,
      long fromMs,
      long toMs,
      long resolutionMs,
      long throughSequence,
      long throughMs
  ) throws IOException {
    Set<String> ids = new LinkedHashSet<>(requestedIds);
    Map<String, Map<Long, HistoryPoint>> selected = new LinkedHashMap<>();
    for (String id : ids) {
      selected.put(id, new LinkedHashMap<>());
    }

    List<HistoryTier> sources = new ArrayList<>();
    for (HistoryTier tier : HistoryTier.values()) {
      if (tier.intervalMs() <= resolutionMs) {
        sources.add(tier);
      }
    }
    sources.sort(Comparator.comparingLong(HistoryTier::intervalMs).reversed());
    for (HistoryTier tier : sources) {
      Map<String, List<HistoryPoint>> tierPoints = store.points(tier, ids, fromMs, toMs);
      if (tier == HistoryTier.RAW) {
        mergePointLists(tierPoints, activePointSource.points(ids, fromMs, toMs));
      }
      Map<String, Map<Long, HistoryPoint>> aggregated = aggregate(tierPoints, fromMs, resolutionMs);
      for (String id : ids) {
        Map<Long, HistoryPoint> target = selected.get(id);
        Map<Long, HistoryPoint> candidates = aggregated.get(id);
        if (candidates == null) {
          continue;
        }
        for (Map.Entry<Long, HistoryPoint> candidate : candidates.entrySet()) {
          target.putIfAbsent(candidate.getKey(), candidate.getValue());
        }
      }
    }

    List<HistoryQuerySeries> series = new ArrayList<>(ids.size());
    for (String id : ids) {
      MetricDescriptor descriptor = store.descriptor(id);
      String name = descriptor == null ? id : descriptor.name();
      String suffix = descriptor == null ? "" : descriptor.suffix();
      List<HistoryPoint> points = new ArrayList<>(selected.get(id).values());
      points.sort(Comparator.comparingLong(HistoryPoint::timestampMs));
      series.add(new HistoryQuerySeries(id, name, suffix, List.copyOf(points)));
    }
    return new HistoryQueryResult(
        fromMs,
        toMs,
        resolutionMs,
        throughSequence,
        throughMs,
        List.copyOf(series)
    );
  }

  private static Map<String, Map<Long, HistoryPoint>> aggregate(
      Map<String, List<HistoryPoint>> source,
      long queryStartMs,
      long resolutionMs
  ) {
    Map<String, Map<Long, MutableAggregate>> accumulators = new HashMap<>();
    for (Map.Entry<String, List<HistoryPoint>> entry : source.entrySet()) {
      Map<Long, MutableAggregate> series = accumulators.computeIfAbsent(entry.getKey(), ignored -> new HashMap<>());
      for (HistoryPoint point : entry.getValue()) {
        long bucket = queryStartMs + Math.floorDiv(point.timestampMs() - queryStartMs, resolutionMs) * resolutionMs;
        series.computeIfAbsent(bucket, ignored -> new MutableAggregate()).add(point);
      }
    }
    Map<String, Map<Long, HistoryPoint>> result = new HashMap<>();
    for (Map.Entry<String, Map<Long, MutableAggregate>> entry : accumulators.entrySet()) {
      Map<Long, HistoryPoint> points = new HashMap<>();
      for (Map.Entry<Long, MutableAggregate> aggregate : entry.getValue().entrySet()) {
        points.put(aggregate.getKey(), aggregate.getValue().point(aggregate.getKey(), resolutionMs));
      }
      result.put(entry.getKey(), points);
    }
    return result;
  }

  private static void mergePointLists(
      Map<String, List<HistoryPoint>> target,
      Map<String, List<HistoryPoint>> source
  ) {
    for (Map.Entry<String, List<HistoryPoint>> entry : source.entrySet()) {
      Map<Long, HistoryPoint> merged = new LinkedHashMap<>();
      for (HistoryPoint point : target.getOrDefault(entry.getKey(), List.of())) {
        merged.put(point.timestampMs(), point);
      }
      for (HistoryPoint point : entry.getValue()) {
        merged.put(point.timestampMs(), point);
      }
      target.put(entry.getKey(), new ArrayList<>(merged.values()));
    }
  }

  private static long ceilDiv(long value, long divisor) {
    return Math.floorDiv(value + divisor - 1L, divisor);
  }

  public interface ActivePointSource {
    Map<String, List<HistoryPoint>> points(Set<String> ids, long fromMs, long toMs);
  }

  private static final class MutableAggregate {
    private double first;
    private double minimum;
    private double maximum;
    private double sum;
    private double last;
    private long count;

    void add(HistoryPoint point) {
      if (count == 0L) {
        first = point.first();
        minimum = point.minimum();
        maximum = point.maximum();
      } else {
        minimum = Math.min(minimum, point.minimum());
        maximum = Math.max(maximum, point.maximum());
      }
      sum += point.sum();
      last = point.last();
      count += point.count();
    }

    HistoryPoint point(long timestampMs, long resolutionMs) {
      return new HistoryPoint(timestampMs, resolutionMs, first, minimum, maximum, sum, last, count);
    }
  }
}
