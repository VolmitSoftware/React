package art.arcane.react.mod;

import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.rendering.MapRendererRegistry;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererIrisMetrics;
import art.arcane.react.api.rendering.RendererIrisWorldMetrics;
import art.arcane.react.api.rendering.RendererReactMetrics;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapChunkSampler;
import art.arcane.react.api.rendering.MapTheme;
import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.content.feature.OwnerCostKindRenderer;
import art.arcane.react.content.feature.OwnerSharePieRenderer;
import art.arcane.react.content.feature.ProfilerOwnerShare;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.mod.cost.CostKind;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.platform.PlatformScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * React's dashboards and feature maps on a mod server. The feature maps are React's own map features, so enabling,
 * disabling and configuring them works as on Bukkit; maps that belong to an integration that is not installed are left
 * out, as Bukkit's map controller does.
 */
public final class ModDashboardMaps {
    private static final List<String> INTEGRATION_IDS = List.of("iris", "adapt", "wormholes", "gloss", "hiddenore", "biletools", "animus");
    private final ModMapData data;
    private final List<ReactRenderer> renderers = new ArrayList<>();

    private ModDashboardMaps(ModMapData data) {
        this.data = data;
    }

    static ModDashboardMaps create(ModReactPlatform platform) {
        ModMapData data = platform.mapData();
        ModDashboardMaps maps = new ModDashboardMaps(data);
        maps.renderers.add(new RendererReactMetrics(SamplerHosts::sampler));
        maps.renderers.add(new ModWorldgenPieRenderer(platform.ownerCost()));
        maps.renderers.add(new OwnerCostKindRenderer(data::eventMeasurement));
        maps.renderers.addAll(sharePies(platform));
        ModIntegrationMetrics integrations = data.integrations();
        if (data.isInstalled("iris")) {
            maps.renderers.addAll(RendererIrisMetrics.dashboards(integrations));
            maps.renderers.addAll(RendererIrisWorldMetrics.dashboards(integrations));
        }
        if (data.isInstalled("wormholes")) {
            maps.renderers.add(ModIntegrationSources.wormholesDashboard(integrations));
        }
        if (data.isInstalled("adapt")) {
            maps.renderers.add(ModIntegrationSources.adaptDashboard(integrations));
        }
        FeatureController features = ReactComponents.features();
        if (features != null && features.getFeatures() != null) {
            for (Feature feature : features.getFeatures().all()) {
                if (feature instanceof ReactRenderer && !maps.absentIntegration(feature.getId())) {
                    maps.renderers.add((ReactRenderer) feature);
                }
            }
        }
        maps.schedule(platform.scheduler(), false);
        data.schedule(platform.scheduler());
        return maps;
    }

    /** A pie per kind of mod cost, chunks held, network traffic and, while it runs, the profiler's owner samples. */
    static List<ReactRenderer> sharePies(ModReactPlatform platform) {
        ModOwnerCost cost = platform.ownerCost();
        ModOwnerGauges gauges = platform.ownerGauges();
        ProfilerOwnerShare profiler = new ProfilerOwnerShare(() -> platform.webServer().map(ReactWebServer::profiler));
        return List.of(
                new OwnerSharePieRenderer("event-mod-pie-map", RendererMessages.TITLE_MOD_EVENT_SHARE, MapTheme.INFO,
                        OwnerSharePieRenderer.Unit.MS_PER_SECOND, () -> cost.kindShare(CostKind.EVENT), RendererMessages.PIE_NO_DATA),
                new OwnerSharePieRenderer("entity-mod-pie-map", RendererMessages.TITLE_MOD_ENTITY_SHARE, MapTheme.OK,
                        OwnerSharePieRenderer.Unit.MS_PER_SECOND, () -> cost.kindShare(CostKind.ENTITY), RendererMessages.PIE_NO_DATA),
                new OwnerSharePieRenderer("block-entity-mod-pie-map", RendererMessages.TITLE_MOD_BLOCK_ENTITY_SHARE, MapTheme.WARN,
                        OwnerSharePieRenderer.Unit.MS_PER_SECOND, () -> cost.kindShare(CostKind.BLOCK_ENTITY), RendererMessages.PIE_NO_DATA),
                new OwnerSharePieRenderer("chunk-loader-mod-pie-map", RendererMessages.TITLE_MOD_CHUNK_LOADER_SHARE, MapTheme.ACCENT_TEAL,
                        OwnerSharePieRenderer.Unit.CHUNKS, gauges::chunkLoaderShare, RendererMessages.PIE_NO_DATA),
                new OwnerSharePieRenderer("network-mod-pie-map", RendererMessages.TITLE_MOD_NETWORK_SHARE, MapTheme.ACCENT_MAGENTA,
                        OwnerSharePieRenderer.Unit.BYTES_PER_SECOND, gauges::networkShare, RendererMessages.PIE_NO_DATA),
                new OwnerSharePieRenderer("profiler-mod-pie-map", RendererMessages.TITLE_MOD_PROFILER_SHARE, MapTheme.ACCENT_PINK,
                        OwnerSharePieRenderer.Unit.SAMPLES, profiler, RendererMessages.PIE_PROFILER_STOPPED));
    }

    /** True for a renderer of an integration mod that is not installed, which Bukkit leaves off its map list too. */
    boolean absentIntegration(String rendererId) {
        String normalized = MapRendererRegistry.normalize(rendererId == null ? "" : rendererId).toLowerCase(Locale.ROOT);
        for (String integration : INTEGRATION_IDS) {
            if (normalized.startsWith(integration + "-")) {
                return !data.isInstalled(integration);
            }
        }
        return false;
    }

    public List<ReactRenderer> renderers() {
        return renderers;
    }

    List<ChunkGridExporter> heatmapExporters() {
        List<ChunkGridExporter> exporters = new ArrayList<>();
        for (ReactRenderer renderer : renderers) {
            if (renderer instanceof ChunkGridExporter) {
                exporters.add((ChunkGridExporter) renderer);
            }
        }
        return exporters;
    }

    HeatmapChunkSampler heatmapSampler() {
        return data.heatmaps();
    }

    public void registerInto(MapRendererRegistry registry) {
        for (ReactRenderer renderer : renderers) {
            registry.register(renderer);
        }
    }

    private void schedule(PlatformScheduler scheduler, boolean wasDemanded) {
        ModChunkHeatmapHost heatmaps = data.heatmaps();
        scheduler.global(() -> {
            boolean demanded = heatmaps.demanded();
            if (demanded) {
                heatmaps.refresh();
            } else if (wasDemanded) {
                heatmaps.release();
            }
            schedule(scheduler, demanded);
        }, ModChunkHeatmapHost.REFRESH_TICKS);
    }
}
