package art.arcane.react.mod;

import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapChunkSampler;
import art.arcane.react.api.rendering.MapRendererRegistry;
import art.arcane.react.api.rendering.MapRuntime;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererIrisMetrics;
import art.arcane.react.api.rendering.RendererIrisWorldMetrics;
import art.arcane.react.api.rendering.RendererReactMetrics;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.content.feature.AdaptAbilityImpactListRenderer;
import art.arcane.react.content.feature.AdaptPressureHeatmap;
import art.arcane.react.content.feature.AnimusCostSharePieRenderer;
import art.arcane.react.content.feature.AnimusNetworkListRenderer;
import art.arcane.react.content.feature.AnimusPressureHeatmap;
import art.arcane.react.content.feature.ChunkCostHeatmap;
import art.arcane.react.content.feature.ChunkHeatmapRenderer;
import art.arcane.react.content.feature.ChunkLoadGenCostHeatmap;
import art.arcane.react.content.feature.EntityPressureHeatmap;
import art.arcane.react.content.feature.HopperThroughputHeatmap;
import art.arcane.react.content.feature.IrisBiomeSharePieRenderer;
import art.arcane.react.content.feature.IrisPressureHeatmap;
import art.arcane.react.content.feature.IrisWorldSharePieRenderer;
import art.arcane.react.content.feature.OwnerEventImpactListRenderer;
import art.arcane.react.content.feature.OwnerImpactPieRenderer;
import art.arcane.react.content.feature.PlayerImpactHeatmap;
import art.arcane.react.content.feature.RedstoneActivityHeatmap;
import art.arcane.react.content.feature.TickSpikeReplayHeatmap;
import art.arcane.react.platform.PlatformScheduler;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * React's own dashboard and feature maps on a mod server. The map runtime registers {@link #renderers()} and passes
 * its frame anchors so framed heatmaps centre on their frame:
 * {@code ModDashboardMaps.create(server, platform, runtime::frameAnchor).registerInto(registry)}.
 */
public final class ModDashboardMaps {
    private final ModChunkHeatmapHost heatmaps;
    private final List<ReactRenderer> renderers;

    private ModDashboardMaps(ModChunkHeatmapHost heatmaps, List<ReactRenderer> renderers) {
        this.heatmaps = heatmaps;
        this.renderers = new ArrayList<>(renderers);
    }

    /** Dashboards for Iris, Wormholes, Adapt and Animus, added only when that mod is installed. */
    private void registerIntegrations(MinecraftServer server, ModReactPlatform platform, ModChunkHeatmapHost heatmaps,
                                      ModMapAnchorHost anchors, ChunkHeatmapRenderer.Settings settings) {
        Predicate<UUID> viewerOnline = (UUID viewerId) -> server.getPlayerList().getPlayer(viewerId) != null;
        List<ModIntegrationMetrics.Source> sources = ModIntegrationSources.installed();
        ModIntegrationMetrics integrations = new ModIntegrationMetrics(server, sources);
        platform.integrations(integrations);
        for (ModIntegrationMetrics.Source source : sources) {
            switch (source.id()) {
                case "iris" -> {
                    renderers.addAll(RendererIrisMetrics.dashboards(integrations));
                    renderers.addAll(RendererIrisWorldMetrics.dashboards(integrations));
                    renderers.add(new IrisWorldSharePieRenderer(integrations, viewerOnline));
                    renderers.add(new IrisPressureHeatmap(heatmaps, anchors, settings, integrations));
                    ModIrisBiomes biomes = new ModIrisBiomes(server);
                    renderers.add(new IrisBiomeSharePieRenderer(biomes::buckets, viewerOnline));
                    biomes.schedule(platform.scheduler());
                }
                case "wormholes" -> renderers.add(ModIntegrationSources.wormholesDashboard(integrations));
                case "adapt" -> {
                    renderers.add(ModIntegrationSources.adaptDashboard(integrations));
                    renderers.add(new AdaptAbilityImpactListRenderer(integrations, anchors, () -> 0));
                    renderers.add(new AdaptPressureHeatmap(heatmaps, anchors, settings, integrations));
                }
                default -> {
                }
            }
        }
        if (integrations.any()) {
            integrations.refresh();
            integrations.schedule(platform.scheduler());
        }
        ModAnimusMetrics animus = ModAnimusMetrics.installed();
        if (animus != null) {
            renderers.add(new AnimusCostSharePieRenderer(animus::snapshot, viewerOnline));
            renderers.add(new AnimusNetworkListRenderer(animus::snapshot, anchors));
            renderers.add(new AnimusPressureHeatmap(heatmaps, anchors, settings, animus::snapshot));
            animus.schedule(platform.scheduler());
        }
    }

    static ModDashboardMaps create(MinecraftServer server, ModReactPlatform platform, IntFunction<MapRuntime.FrameAnchor> frameAnchors) {
        ModSamplerHost samplerHost = (ModSamplerHost) platform.samplerHost().orElseThrow();
        ModChunkHeatmapHost heatmaps = new ModChunkHeatmapHost(server, samplerHost.chunks());
        ModMapAnchorHost anchors = new ModMapAnchorHost(server, frameAnchors);
        ModOwnerCost ownerCost = platform.ownerCost();
        ChunkHeatmapRenderer.Settings settings = ChunkHeatmapRenderer.DEFAULT_SETTINGS;
        ModTickSpikeReplay replay = new ModTickSpikeReplay(server, samplerHost.chunks());
        ModDashboardMaps maps = new ModDashboardMaps(heatmaps, List.of(
                new RendererReactMetrics(SamplerHosts::sampler),
                new ChunkCostHeatmap(heatmaps, anchors, settings),
                new ChunkLoadGenCostHeatmap(heatmaps, anchors, settings),
                new EntityPressureHeatmap(heatmaps, anchors, settings),
                new RedstoneActivityHeatmap(heatmaps, anchors, settings),
                new HopperThroughputHeatmap(heatmaps, anchors, settings),
                new PlayerImpactHeatmap(heatmaps, anchors, PlayerImpactHeatmap.DEFAULT_PLAYER_SETTINGS),
                new OwnerEventImpactListRenderer(anchors, () -> ownerCost, () -> 0),
                new OwnerImpactPieRenderer(() -> ownerCost, (UUID viewerId) -> server.getPlayerList().getPlayer(viewerId) != null),
                new TickSpikeReplayHeatmap(heatmaps, anchors, settings, replay.replay())
        ));
        maps.schedule(platform.scheduler(), false);
        replay.schedule(platform.scheduler());
        maps.registerIntegrations(server, platform, heatmaps, anchors, settings);
        return maps;
    }

    public List<ReactRenderer> renderers() {
        return renderers;
    }

    List<ChunkGridExporter> heatmapExporters() {
        List<ChunkGridExporter> exporters = new ArrayList<>();
        for (ReactRenderer renderer : renderers) {
            if (renderer instanceof ChunkGridExporter exporter) {
                exporters.add(exporter);
            }
        }
        return exporters;
    }

    HeatmapChunkSampler heatmapSampler() {
        return heatmaps;
    }

    public void registerInto(MapRendererRegistry registry) {
        for (ReactRenderer renderer : renderers) {
            registry.register(renderer);
        }
    }

    private void schedule(PlatformScheduler scheduler, boolean wasDemanded) {
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
