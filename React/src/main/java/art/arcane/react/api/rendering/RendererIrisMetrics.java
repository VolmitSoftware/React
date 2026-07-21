package art.arcane.react.api.rendering;

import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSchema;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RendererIrisMetrics extends RendererIntegrationMetricsBase {
  public static final String ID = "iris-metrics";

  private final String id;
  private final String panelTitle;
  private final List<MetricLine> metrics;

  public RendererIrisMetrics() {
    this(
        ID,
        "Iris Overview",
        List.of(
            metric(IntegrationMetricSchema.IRIS_WORLD_COUNT, "Worlds", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_ACTIVE, "Active", 0, ""),
            metric(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, "Chunks", 0, ""),
            metric(IntegrationMetricSchema.IRIS_LOADED_ENTITIES, "Entities", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, "Chunk Rate", 1, " ch/s"),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL, "Generated", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_FAILED, "Failed", 0, "")
        )
    );
  }

  private RendererIrisMetrics(String id, String panelTitle, List<MetricLine> metrics) {
    this.id = id;
    this.panelTitle = panelTitle;
    this.metrics = List.copyOf(metrics);
  }

  public static List<RendererIrisMetrics> dashboards() {
    return List.of(
        new RendererIrisMetrics(),
        panel("iris-engine-activity", "Iris Engine Activity",
            metric(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_SESSION, "Session Chunks", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL, "Lifetime Chunks", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, "Chunk Rate", 1, " ch/s"),
            metric(IntegrationMetricSchema.IRIS_BLOCK_UPDATES_PER_SECOND, "Block Updates", 0, " /s"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_ACTIVE_LEASES, "Active Leases", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_PARALLELISM, "Parallelism", 0, ""),
            metric(IntegrationMetricSchema.IRIS_HOTLOADS_TOTAL, "Hotloads", 0, "")),
        panel("iris-engine-lifecycle", "Iris Lifecycle",
            metric(IntegrationMetricSchema.IRIS_ENGINE_ACTIVE, "Active", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_CLOSING, "Closing", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_FAILED, "Failed", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_STUDIO, "Studio", 0, ""),
            metric(IntegrationMetricSchema.IRIS_MAINTENANCE_ACTIVE_TASKS, "Maintenance", 0, ""),
            metric(IntegrationMetricSchema.IRIS_MAINTENANCE_WORKERS, "Workers", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_PENDING_REGISTRATIONS, "Registering", 0, "")),
        panel("iris-mantle-health", "Iris Mantle Health",
            metric(IntegrationMetricSchema.IRIS_MANTLE_RESIDENT_PLATES, "Resident", 0, ""),
            metric(IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, "Unload Queue", 0, ""),
            metric(IntegrationMetricSchema.IRIS_MANTLE_IDLE_AVERAGE_MS, "Idle Avg", 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_MANTLE_IDLE_MAX_MS, "Idle Max", 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_MANTLE_IDLE_MIN_MS, "Idle Min", 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_MANTLE_HEAP_USAGE, "Heap", 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_MANTLE_RECLAIM_URGENCY, "Reclaim", 1, "%", 100D)),
        panel("iris-cache-overview", "Iris Cache Overview",
            metric(IntegrationMetricSchema.IRIS_CACHE_COUNT, "Caches", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_ENTRIES, "Entries", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_CAPACITY, "Capacity", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_USAGE, "Usage", 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_CACHE_RESOURCE_COUNT, "Resource Caches", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_RESOURCE_ENTRIES, "Resource Entries", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_RESOURCE_CAPACITY, "Resource Cap", 0, "")),
        panel("iris-cache-streams", "Iris Stream Caches",
            metric(IntegrationMetricSchema.IRIS_CACHE_RESOURCE_USAGE, "Resource Use", 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_COUNT, "2D Caches", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_ENTRIES, "2D Entries", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_CAPACITY, "2D Capacity", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_USAGE, "2D Usage", 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_COUNT, "3D Caches", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_ENTRIES, "3D Entries", 0, "")),
        panel("iris-cache-other", "Iris 3D and Other Caches",
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_CAPACITY, "3D Capacity", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_USAGE, "3D Usage", 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_CACHE_OTHER_COUNT, "Other Caches", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_OTHER_ENTRIES, "Other Entries", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_OTHER_CAPACITY, "Other Capacity", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_OTHER_USAGE, "Other Usage", 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_ENTITY_SATURATION, "Entity Saturation", 1, "%", 100D)),
        panel("iris-pregen-progress", "Iris Pregen Progress",
            metric(IntegrationMetricSchema.IRIS_PREGEN_ACTIVE, "Active", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_PAUSED, "Paused", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_PROGRESS, "Progress", 1, "%"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_GENERATED, "Generated", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_TOTAL, "Total", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_QUEUE, "Remaining", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT, "Throughput", 1, " ch/s")),
        panel("iris-pregen-health", "Iris Pregen Health",
            metric(IntegrationMetricSchema.IRIS_PREGEN_ETA_MS, "ETA", 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_ELAPSED_MS, "Elapsed", 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_FAILED, "Failed Chunks", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_PERMIT_MS, "Permit Wait", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_ADAPTIVE_MS, "Adaptive Wait", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_MANTLE_RECLAIM_URGENCY, "Reclaim", 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, "Unload Queue", 0, "")),
        panel("iris-generation-pipeline", "Iris Generation Pipeline",
            metric(IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS, "Total", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_TERRAIN_MS, "Terrain", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_BIOME_MS, "Biome", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_POST_MS, "Post", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_PERFECTION_MS, "Perfection", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_DECORATION_MS, "Decoration", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_UPDATES_MS, "Updates", 2, " ms")),
        panel("iris-generation-features", "Iris Generation Features",
            metric(IntegrationMetricSchema.IRIS_GENERATION_CAVE_MS, "Caves", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_DEPOSIT_MS, "Deposits", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CONTEXT_PREFILL_MS, "Context Prefill", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CARVE_RESOLVE_MS, "Carve Resolve", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CARVE_APPLY_MS, "Carve Apply", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, "Loaded Chunks", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, "Chunk Rate", 1, " ch/s"))
    );
  }

  public static Set<String> dashboardMetricKeys() {
    Set<String> keys = new LinkedHashSet<>();
    for (RendererIrisMetrics dashboard : dashboards()) {
      for (MetricLine metric : dashboard.metricLines()) {
        keys.add(metric.key());
      }
    }
    return Set.copyOf(keys);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  protected String pluginId() {
    return "iris";
  }

  @Override
  protected String title() {
    return panelTitle;
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(10, 22, 18);
  }

  @Override
  protected TinyColor accentColor() {
    return new TinyColor(55, 145, 100);
  }

  @Override
  protected List<MetricLine> metricLines() {
    return metrics;
  }

  private static RendererIrisMetrics panel(String id, String title, MetricLine... metrics) {
    return new RendererIrisMetrics(id, title, List.of(metrics));
  }

  private static MetricLine metric(String key, String label, int decimals, String suffix) {
    return new MetricLine(key, label, decimals, suffix);
  }

  private static MetricLine metric(String key, String label, int decimals, String suffix, double scale) {
    return new MetricLine(key, label, decimals, suffix, scale);
  }
}
