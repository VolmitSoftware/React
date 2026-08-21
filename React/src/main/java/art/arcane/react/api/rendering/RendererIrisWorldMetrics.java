package art.arcane.react.api.rendering;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.World;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RendererIrisWorldMetrics extends RendererIntegrationMetricsBase {
  private final String id;
  private final TextKey panelTitle;
  private final List<MetricLine> metrics;

  private RendererIrisWorldMetrics(String id, TextKey panelTitle, List<MetricLine> metrics) {
    this.id = id;
    this.panelTitle = panelTitle;
    this.metrics = List.copyOf(metrics);
  }

  public static List<RendererIrisWorldMetrics> dashboards() {
    return List.of(
        panel("iris-world-overview", RendererMessages.TITLE_IRIS_WORLD_OVERVIEW,
            metric(IntegrationMetricSchema.IRIS_ENGINE_ACTIVE, RendererMessages.METRIC_ACTIVE, 0, ""),
            metric(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, RendererMessages.METRIC_CHUNKS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_LOADED_ENTITIES, RendererMessages.METRIC_ENTITIES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENTITY_SATURATION, RendererMessages.METRIC_SATURATION, 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, RendererMessages.METRIC_CHUNK_RATE, 1, " ch/s"),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_SESSION, RendererMessages.METRIC_SESSION_CHUNKS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL, RendererMessages.METRIC_LIFETIME_CHUNKS, 0, "")),
        panel("iris-world-engine", RendererMessages.TITLE_IRIS_WORLD_ENGINE,
            metric(IntegrationMetricSchema.IRIS_BLOCK_UPDATES_PER_SECOND, RendererMessages.METRIC_BLOCK_UPDATES, 0, " /s"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_ACTIVE_LEASES, RendererMessages.METRIC_ACTIVE_LEASES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_PARALLELISM, RendererMessages.METRIC_PARALLELISM, 0, ""),
            metric(IntegrationMetricSchema.IRIS_HOTLOADS_TOTAL, RendererMessages.METRIC_HOTLOADS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_CLOSING, RendererMessages.METRIC_CLOSING, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_FAILED, RendererMessages.METRIC_FAILED, 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_STUDIO, RendererMessages.METRIC_STUDIO, 0, "")),
        panel("iris-world-mantle", RendererMessages.TITLE_IRIS_WORLD_MANTLE,
            metric(IntegrationMetricSchema.IRIS_MANTLE_RESIDENT_PLATES, RendererMessages.METRIC_RESIDENT, 0, ""),
            metric(IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, RendererMessages.METRIC_UNLOAD_QUEUE, 0, ""),
            metric(IntegrationMetricSchema.IRIS_MANTLE_IDLE_AVERAGE_MS, RendererMessages.METRIC_IDLE, 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, RendererMessages.METRIC_LOADED_CHUNKS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_GENERATION_ACTIVE_LEASES, RendererMessages.METRIC_GENERATION_LEASES, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, RendererMessages.METRIC_CHUNK_RATE, 1, " ch/s"),
            metric(IntegrationMetricSchema.IRIS_BLOCK_UPDATES_PER_SECOND, RendererMessages.METRIC_BLOCK_UPDATES, 0, " /s")),
        panel("iris-world-generation", RendererMessages.TITLE_IRIS_WORLD_GENERATION,
            metric(IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS, RendererMessages.METRIC_TOTAL, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_TERRAIN_MS, RendererMessages.METRIC_TERRAIN, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_BIOME_MS, RendererMessages.METRIC_BIOME, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_POST_MS, RendererMessages.METRIC_POST, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_PERFECTION_MS, RendererMessages.METRIC_PERFECTION, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_DECORATION_MS, RendererMessages.METRIC_DECORATION, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_UPDATES_MS, RendererMessages.METRIC_UPDATES, 2, " ms")),
        panel("iris-world-features", RendererMessages.TITLE_IRIS_WORLD_FEATURES,
            metric(IntegrationMetricSchema.IRIS_GENERATION_CAVE_MS, RendererMessages.METRIC_CAVES, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_DEPOSIT_MS, RendererMessages.METRIC_DEPOSITS, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CONTEXT_PREFILL_MS, RendererMessages.METRIC_CONTEXT_PREFILL, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CARVE_RESOLVE_MS, RendererMessages.METRIC_CARVE_RESOLVE, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CARVE_APPLY_MS, RendererMessages.METRIC_CARVE_APPLY, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_PERMIT_MS, RendererMessages.METRIC_PERMIT_WAIT, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_ADAPTIVE_MS, RendererMessages.METRIC_ADAPTIVE_WAIT, 2, " ms")),
        panel("iris-world-pregen", RendererMessages.TITLE_IRIS_WORLD_PREGEN,
            metric(IntegrationMetricSchema.IRIS_PREGEN_ACTIVE, RendererMessages.METRIC_ACTIVE, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_PAUSED, RendererMessages.METRIC_PAUSED, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_PROGRESS, RendererMessages.METRIC_PROGRESS, 1, "%"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_GENERATED, RendererMessages.METRIC_GENERATED, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_TOTAL, RendererMessages.METRIC_TOTAL, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_QUEUE, RendererMessages.METRIC_REMAINING, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT, RendererMessages.METRIC_THROUGHPUT, 1, " ch/s")),
        panel("iris-world-pregen-time", RendererMessages.TITLE_IRIS_WORLD_PREGEN_TIME,
            metric(IntegrationMetricSchema.IRIS_PREGEN_ETA_MS, RendererMessages.METRIC_ETA, 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_ELAPSED_MS, RendererMessages.METRIC_ELAPSED, 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_FAILED, RendererMessages.METRIC_FAILED_CHUNKS, 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_PERMIT_MS, RendererMessages.METRIC_PERMIT_WAIT, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_ADAPTIVE_MS, RendererMessages.METRIC_ADAPTIVE_WAIT, 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, RendererMessages.METRIC_UNLOAD_QUEUE, 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, RendererMessages.METRIC_ENGINE_RATE, 1, " ch/s"))
    );
  }

  public static Set<String> dashboardMetricKeys() {
    Set<String> keys = new LinkedHashSet<>();
    for (RendererIrisWorldMetrics dashboard : dashboards()) {
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
    return new TinyColor(8, 18, 23);
  }

  @Override
  protected TinyColor accentColor() {
    return new TinyColor(66, 156, 188);
  }

  @Override
  protected List<MetricLine> metricLines() {
    return metrics;
  }

  @Override
  protected String headerValue() {
    IntegrationMetricGroup group = currentGroup(null);
    return group == null
        ? ReactLanguage.raw(RendererMessages.STATUS_NO_WORLD)
        : fitText(group.label(), 54 * uiScale());
  }

  @Override
  protected IntegrationMetricSample metricSample(RemoteSamplerBridge bridge, MetricLine metric) {
    IntegrationMetricGroup group = currentGroup(bridge);
    if (group == null) {
      return IntegrationMetricSample.unavailable(
          IntegrationMetricSchema.descriptor(metric.key()),
          "world-not-managed",
          System.currentTimeMillis()
      );
    }
    IntegrationMetricSample sample = group.samples().get(metric.key());
    return sample == null
        ? IntegrationMetricSample.unavailable(
            IntegrationMetricSchema.descriptor(metric.key()),
            "world-metric-not-published",
            System.currentTimeMillis()
        )
        : sample;
  }

  private IntegrationMetricGroup currentGroup(RemoteSamplerBridge providedBridge) {
    RemoteSamplerBridge bridge = providedBridge;
    if (bridge == null) {
      IntegrationController controller = React.controller(IntegrationController.class);
      bridge = controller == null ? null : controller.getRemoteSamplerBridge();
    }
    World world = view() != null ? view().getWorld() : null;
    if (world == null && player() != null) {
      world = player().getWorld();
    }
    if (bridge == null || world == null) {
      return null;
    }
    return bridge.getGroup("iris", "world", WorldIdentity.serialize(world));
  }

  private static RendererIrisWorldMetrics panel(String id, TextKey title, MetricLine... metrics) {
    return new RendererIrisWorldMetrics(id, title, List.of(metrics));
  }

  private static MetricLine metric(String key, TextKey label, int decimals, String suffix) {
    return new MetricLine(key, label, decimals, suffix);
  }

  private static MetricLine metric(String key, TextKey label, int decimals, String suffix, double scale) {
    return new MetricLine(key, label, decimals, suffix, scale);
  }
}
