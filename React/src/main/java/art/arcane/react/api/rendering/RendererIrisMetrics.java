package art.arcane.react.api.rendering;

import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RendererIrisMetrics extends RendererIntegrationMetricsBase {
  public static final String ID = "iris-metrics";

  private final String id;
  private final TextKey panelTitle;
  private final List<MetricLine> metrics;

  public RendererIrisMetrics() {
    this(
        ID,
        RendererMessages.TITLE_IRIS_OVERVIEW,
        List.of(
            metric(IntegrationMetricSchema.IRIS_WORLD_COUNT, RendererMessages.METRIC_WORLDS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_ACTIVE, RendererMessages.METRIC_ACTIVE, 0, ""),
            metric(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, RendererMessages.METRIC_CHUNKS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_LOADED_ENTITIES, RendererMessages.METRIC_ENTITIES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, RendererMessages.METRIC_CHUNK_RATE, 1, " ch/s"),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL, RendererMessages.METRIC_GENERATED, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_FAILED, RendererMessages.METRIC_FAILED, 0, "")
        )
    );
  }

  private RendererIrisMetrics(String id, TextKey panelTitle, List<MetricLine> metrics) {
    this.id = id;
    this.panelTitle = panelTitle;
    this.metrics = List.copyOf(metrics);
  }

  public static List<RendererIrisMetrics> dashboards() {
    return List.of(
        new RendererIrisMetrics(),
        panel("iris-engine-activity", RendererMessages.TITLE_IRIS_ENGINE_ACTIVITY,
            metric(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_SESSION, RendererMessages.METRIC_SESSION_CHUNKS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL, RendererMessages.METRIC_LIFETIME_CHUNKS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, RendererMessages.METRIC_CHUNK_RATE, 1, " ch/s"),
            metric(IntegrationMetricSchema.IRIS_BLOCK_UPDATES_PER_SECOND, RendererMessages.METRIC_BLOCK_UPDATES, 0, " /s"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_ACTIVE_LEASES, RendererMessages.METRIC_ACTIVE_LEASES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_PARALLELISM, RendererMessages.METRIC_PARALLELISM, 0, ""),
            metric(IntegrationMetricSchema.IRIS_HOTLOADS_TOTAL, RendererMessages.METRIC_HOTLOADS, 0, "")),
        panel("iris-engine-lifecycle", RendererMessages.TITLE_IRIS_LIFECYCLE,
            metric(IntegrationMetricSchema.IRIS_ENGINE_ACTIVE, RendererMessages.METRIC_ACTIVE, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_CLOSING, RendererMessages.METRIC_CLOSING, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_FAILED, RendererMessages.METRIC_FAILED, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_STUDIO, RendererMessages.METRIC_STUDIO, 0, ""),
            metric(IntegrationMetricSchema.IRIS_MAINTENANCE_ACTIVE_TASKS, RendererMessages.METRIC_MAINTENANCE, 0, ""),
            metric(IntegrationMetricSchema.IRIS_MAINTENANCE_WORKERS, RendererMessages.METRIC_WORKERS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_PENDING_REGISTRATIONS, RendererMessages.METRIC_REGISTERING, 0, "")),
        panel("iris-mantle-health", RendererMessages.TITLE_IRIS_MANTLE_HEALTH,
            metric(IntegrationMetricSchema.IRIS_MANTLE_RESIDENT_PLATES, RendererMessages.METRIC_RESIDENT, 0, ""),
            metric(IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, RendererMessages.METRIC_UNLOAD_QUEUE, 0, ""),
            metric(IntegrationMetricSchema.IRIS_MANTLE_IDLE_AVERAGE_MS, RendererMessages.METRIC_IDLE_AVERAGE, 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_MANTLE_IDLE_MAX_MS, RendererMessages.METRIC_IDLE_MAXIMUM, 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_MANTLE_IDLE_MIN_MS, RendererMessages.METRIC_IDLE_MINIMUM, 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_MANTLE_HEAP_USAGE, RendererMessages.METRIC_HEAP, 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_MANTLE_RECLAIM_URGENCY, RendererMessages.METRIC_RECLAIM, 1, "%", 100D)),
        panel("iris-cache-overview", RendererMessages.TITLE_IRIS_CACHE_OVERVIEW,
            metric(IntegrationMetricSchema.IRIS_CACHE_COUNT, RendererMessages.METRIC_CACHES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_ENTRIES, RendererMessages.METRIC_ENTRIES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_CAPACITY, RendererMessages.METRIC_CAPACITY, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_USAGE, RendererMessages.METRIC_USAGE, 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_CACHE_RESOURCE_COUNT, RendererMessages.METRIC_RESOURCE_CACHES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_RESOURCE_ENTRIES, RendererMessages.METRIC_RESOURCE_ENTRIES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_RESOURCE_CAPACITY, RendererMessages.METRIC_RESOURCE_CAPACITY_SHORT, 0, "")),
        panel("iris-cache-streams", RendererMessages.TITLE_IRIS_STREAM_CACHES,
            metric(IntegrationMetricSchema.IRIS_CACHE_RESOURCE_USAGE, RendererMessages.METRIC_RESOURCE_USAGE, 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_COUNT, RendererMessages.METRIC_2D_CACHES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_ENTRIES, RendererMessages.METRIC_2D_ENTRIES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_CAPACITY, RendererMessages.METRIC_2D_CAPACITY, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_USAGE, RendererMessages.METRIC_2D_USAGE, 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_COUNT, RendererMessages.METRIC_3D_CACHES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_ENTRIES, RendererMessages.METRIC_3D_ENTRIES, 0, "")),
        panel("iris-cache-other", RendererMessages.TITLE_IRIS_OTHER_CACHES,
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_CAPACITY, RendererMessages.METRIC_3D_CAPACITY, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_USAGE, RendererMessages.METRIC_3D_USAGE, 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_CACHE_OTHER_COUNT, RendererMessages.METRIC_OTHER_CACHES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_OTHER_ENTRIES, RendererMessages.METRIC_OTHER_ENTRIES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_OTHER_CAPACITY, RendererMessages.METRIC_OTHER_CAPACITY, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CACHE_OTHER_USAGE, RendererMessages.METRIC_OTHER_USAGE, 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_ENTITY_SATURATION, RendererMessages.METRIC_ENTITY_SATURATION, 1, "%", 100D)),
        panel("iris-pregen-progress", RendererMessages.TITLE_IRIS_PREGEN_PROGRESS,
            metric(IntegrationMetricSchema.IRIS_PREGEN_ACTIVE, RendererMessages.METRIC_ACTIVE, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_PAUSED, RendererMessages.METRIC_PAUSED, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_PROGRESS, RendererMessages.METRIC_PROGRESS, 1, "%"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_GENERATED, RendererMessages.METRIC_GENERATED, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_TOTAL, RendererMessages.METRIC_TOTAL, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_QUEUE, RendererMessages.METRIC_REMAINING, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT, RendererMessages.METRIC_THROUGHPUT, 1, " ch/s")),
        panel("iris-pregen-health", RendererMessages.TITLE_IRIS_PREGEN_HEALTH,
            metric(IntegrationMetricSchema.IRIS_PREGEN_ETA_MS, RendererMessages.METRIC_ETA, 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_ELAPSED_MS, RendererMessages.METRIC_ELAPSED, 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_FAILED, RendererMessages.METRIC_FAILED_CHUNKS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_PERMIT_MS, RendererMessages.METRIC_PERMIT_WAIT, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_ADAPTIVE_MS, RendererMessages.METRIC_ADAPTIVE_WAIT, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_MANTLE_RECLAIM_URGENCY, RendererMessages.METRIC_RECLAIM, 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, RendererMessages.METRIC_UNLOAD_QUEUE, 0, "")),
        panel("iris-generation-pipeline", RendererMessages.TITLE_IRIS_GENERATION_PIPELINE,
            metric(IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS, RendererMessages.METRIC_TOTAL, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_TERRAIN_MS, RendererMessages.METRIC_TERRAIN, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_BIOME_MS, RendererMessages.METRIC_BIOME, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_POST_MS, RendererMessages.METRIC_POST, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_PERFECTION_MS, RendererMessages.METRIC_PERFECTION, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_DECORATION_MS, RendererMessages.METRIC_DECORATION, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_UPDATES_MS, RendererMessages.METRIC_UPDATES, 2, " ms")),
        panel("iris-generation-features", RendererMessages.TITLE_IRIS_GENERATION_FEATURES,
            metric(IntegrationMetricSchema.IRIS_GENERATION_CAVE_MS, RendererMessages.METRIC_CAVES, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_DEPOSIT_MS, RendererMessages.METRIC_DEPOSITS, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CONTEXT_PREFILL_MS, RendererMessages.METRIC_CONTEXT_PREFILL, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CARVE_RESOLVE_MS, RendererMessages.METRIC_CARVE_RESOLVE, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CARVE_APPLY_MS, RendererMessages.METRIC_CARVE_APPLY, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, RendererMessages.METRIC_LOADED_CHUNKS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, RendererMessages.METRIC_CHUNK_RATE, 1, " ch/s"))
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
  protected TextKey title() {
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

  private static RendererIrisMetrics panel(String id, TextKey title, MetricLine... metrics) {
    return new RendererIrisMetrics(id, title, List.of(metrics));
  }

  private static MetricLine metric(String key, TextKey label, int decimals, String suffix) {
    return new MetricLine(key, label, decimals, suffix);
  }

  private static MetricLine metric(String key, TextKey label, int decimals, String suffix, double scale) {
    return new MetricLine(key, label, decimals, suffix, scale);
  }
}
