package art.arcane.react.core.telemetry;

import java.util.concurrent.atomic.LongAdder;

public final class ObservationMetrics {
  private static final Counter[] COUNTERS = Counter.values();
  private static final LongAdder[] VALUES = createValues();

  private ObservationMetrics() {
  }

  public static void add(Counter counter, long amount) {
    VALUES[counter.ordinal()].add(amount);
  }

  public static void increment(Counter counter) {
    VALUES[counter.ordinal()].increment();
  }

  public static long[] drain() {
    long[] values = new long[VALUES.length];
    for (int index = 0; index < values.length; index++) {
      values[index] = VALUES[index].sumThenReset();
    }
    return values;
  }

  private static LongAdder[] createValues() {
    LongAdder[] values = new LongAdder[COUNTERS.length];
    for (int index = 0; index < values.length; index++) {
      values[index] = new LongAdder();
    }
    return values;
  }

  public enum Counter {
    NEARBY_QUERIES("react-nearby-queries", "Nearby Entity Queries", false),
    NEARBY_CANDIDATES("react-nearby-candidates", "Nearby Entity Candidates", false),
    NEARBY_SAMPLES("react-nearby-samples", "Nearby Entity Samples", false),
    NEARBY_CACHE_HITS("react-nearby-cache-hits", "Nearby Entity Cache Hits", false),
    NEARBY_QUERY_NANOS("react-nearby-query-ms", "Nearby Entity Query Time", true),
    HEATMAP_SCANS("react-heatmap-scans", "Heatmap Scans", false),
    HEATMAP_CACHE_HITS("react-heatmap-cache-hits", "Heatmap Cache Hits", false),
    HEATMAP_CELLS("react-heatmap-cells", "Heatmap Chunks Inspected", false),
    HEATMAP_SCAN_NANOS("react-heatmap-scan-ms", "Heatmap Scan Time", true),
    MAP_RENDERS("react-map-renders", "Map Renders", false),
    MAP_RENDER_NANOS("react-map-render-ms", "Map Render Time", true),
    MAP_FLUSH_NANOS("react-map-flush-ms", "Map Flush Time", true),
    MAP_THROTTLE_SKIPS("react-map-throttle-skips", "Map Throttle Skips", false),
    MAP_BUSY_SKIPS("react-map-busy-skips", "Map Busy Skips", false),
    MAP_FAILURES("react-map-failures", "Map Render Failures", false),
    ENTITY_SCAN_SKIPS("react-entity-scan-skips", "Entity Scan Busy Skips", false),
    ENTITY_SCAN_COMPLETED("react-entity-scan-completed", "Entity Scan Anchors Completed", false);

    private final String id;
    private final String displayName;
    private final boolean duration;

    Counter(String id, String displayName, boolean duration) {
      this.id = id;
      this.displayName = displayName;
      this.duration = duration;
    }

    public String id() {
      return id;
    }

    public String displayName() {
      return displayName;
    }

    public String unit() {
      return duration ? "ms/s" : "/s";
    }

    public double rate(long value, long elapsedNanos) {
      if (elapsedNanos <= 0L) {
        return 0D;
      }
      return value * (duration ? 1_000D : 1_000_000_000D) / elapsedNanos;
    }
  }
}
