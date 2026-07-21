package art.arcane.react.api.rendering;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.World;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RendererIrisWorldMetrics extends RendererIntegrationMetricsBase {
  private final String id;
  private final String panelTitle;
  private final List<MetricLine> metrics;

  private RendererIrisWorldMetrics(String id, String panelTitle, List<MetricLine> metrics) {
    this.id = id;
    this.panelTitle = panelTitle;
    this.metrics = List.copyOf(metrics);
  }

  public static List<RendererIrisWorldMetrics> dashboards() {
    return List.of(
        panel("iris-world-overview", "Iris World Overview",
            metric(IntegrationMetricSchema.IRIS_ENGINE_ACTIVE, "Active", 0, ""),
            metric(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, "Chunks", 0, ""),
            metric(IntegrationMetricSchema.IRIS_LOADED_ENTITIES, "Entities", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENTITY_SATURATION, "Saturation", 1, "%", 100D),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, "Chunk Rate", 1, " ch/s"),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_SESSION, "Session Chunks", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL, "Lifetime Chunks", 0, "")),
        panel("iris-world-engine", "Iris World Engine",
            metric(IntegrationMetricSchema.IRIS_BLOCK_UPDATES_PER_SECOND, "Block Updates", 0, " /s"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_ACTIVE_LEASES, "Active Leases", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_PARALLELISM, "Parallelism", 0, ""),
            metric(IntegrationMetricSchema.IRIS_HOTLOADS_TOTAL, "Hotloads", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_CLOSING, "Closing", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_FAILED, "Failed", 0, ""),
            metric(IntegrationMetricSchema.IRIS_ENGINE_STUDIO, "Studio", 0, "")),
        panel("iris-world-mantle", "Iris World Mantle",
            metric(IntegrationMetricSchema.IRIS_MANTLE_RESIDENT_PLATES, "Resident", 0, ""),
            metric(IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, "Unload Queue", 0, ""),
            metric(IntegrationMetricSchema.IRIS_MANTLE_IDLE_AVERAGE_MS, "Idle", 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, "Loaded Chunks", 0, ""),
            metric(IntegrationMetricSchema.IRIS_GENERATION_ACTIVE_LEASES, "Generation Leases", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, "Chunk Rate", 1, " ch/s"),
            metric(IntegrationMetricSchema.IRIS_BLOCK_UPDATES_PER_SECOND, "Block Updates", 0, " /s")),
        panel("iris-world-generation", "Iris World Generation",
            metric(IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS, "Total", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_TERRAIN_MS, "Terrain", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_BIOME_MS, "Biome", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_POST_MS, "Post", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_PERFECTION_MS, "Perfection", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_DECORATION_MS, "Decoration", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_UPDATES_MS, "Updates", 2, " ms")),
        panel("iris-world-features", "Iris World Features",
            metric(IntegrationMetricSchema.IRIS_GENERATION_CAVE_MS, "Caves", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_DEPOSIT_MS, "Deposits", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CONTEXT_PREFILL_MS, "Context Prefill", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CARVE_RESOLVE_MS, "Carve Resolve", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_GENERATION_CARVE_APPLY_MS, "Carve Apply", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_PERMIT_MS, "Permit Wait", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_ADAPTIVE_MS, "Adaptive Wait", 2, " ms")),
        panel("iris-world-pregen", "Iris World Pregen",
            metric(IntegrationMetricSchema.IRIS_PREGEN_ACTIVE, "Active", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_PAUSED, "Paused", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_PROGRESS, "Progress", 1, "%"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_GENERATED, "Generated", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_TOTAL, "Total", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_QUEUE, "Remaining", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT, "Throughput", 1, " ch/s")),
        panel("iris-world-pregen-time", "Iris World Pregen Time",
            metric(IntegrationMetricSchema.IRIS_PREGEN_ETA_MS, "ETA", 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_ELAPSED_MS, "Elapsed", 0, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_FAILED, "Failed Chunks", 0, ""),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_PERMIT_MS, "Permit Wait", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_PREGEN_WAIT_ADAPTIVE_MS, "Adaptive Wait", 2, " ms"),
            metric(IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, "Unload Queue", 0, ""),
            metric(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, "Engine Rate", 1, " ch/s"))
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
  protected String title() {
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
    return group == null ? "NO WORLD" : fitText(group.label(), 54 * uiScale());
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

  private static RendererIrisWorldMetrics panel(String id, String title, MetricLine... metrics) {
    return new RendererIrisWorldMetrics(id, title, List.of(metrics));
  }

  private static MetricLine metric(String key, String label, int decimals, String suffix) {
    return new MetricLine(key, label, decimals, suffix);
  }

  private static MetricLine metric(String key, String label, int decimals, String suffix, double scale) {
    return new MetricLine(key, label, decimals, suffix, scale);
  }
}
