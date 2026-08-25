package art.arcane.react.core.history;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HistorySegment {
  private final HistoryTier tier;
  private final long startMs;
  private final int bucketCount;
  private final Map<String, HistorySeries> seriesById;

  public HistorySegment(HistoryTier tier, long startMs, int bucketCount) {
    this.tier = tier;
    this.startMs = startMs;
    this.bucketCount = bucketCount;
    this.seriesById = new LinkedHashMap<>();
  }

  public HistoryTier tier() {
    return tier;
  }

  public long startMs() {
    return startMs;
  }

  public long endMs() {
    return startMs + (tier.intervalMs() * bucketCount);
  }

  public int bucketCount() {
    return bucketCount;
  }

  public Collection<HistorySeries> series() {
    return seriesById.values();
  }

  public HistorySeries series(String id) {
    return seriesById.get(id);
  }

  public HistorySeries series(String id, String name, String suffix) {
    HistorySeries series = seriesById.computeIfAbsent(
        id,
        ignored -> new HistorySeries(id, name, suffix, bucketCount)
    );
    series.metadata(name, suffix);
    return series;
  }

  public void add(HistorySeries series) {
    if (series.bucketCount() != bucketCount) {
      throw new IllegalArgumentException("History series bucket count does not match its segment");
    }
    seriesById.put(series.id(), series);
  }
}
