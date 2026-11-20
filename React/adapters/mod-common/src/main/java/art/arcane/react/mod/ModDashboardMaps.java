package art.arcane.react.mod;

import art.arcane.react.api.rendering.MapRendererRegistry;
import art.arcane.react.api.rendering.MapRuntime;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererReactMetrics;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.content.feature.ChunkCostHeatmap;
import art.arcane.react.content.feature.ChunkHeatmapRenderer;
import art.arcane.react.content.feature.ChunkLoadGenCostHeatmap;
import art.arcane.react.content.feature.EntityPressureHeatmap;
import art.arcane.react.content.feature.HopperThroughputHeatmap;
import art.arcane.react.content.feature.OwnerEventImpactListRenderer;
import art.arcane.react.content.feature.PlayerImpactHeatmap;
import art.arcane.react.content.feature.RedstoneActivityHeatmap;
import art.arcane.react.platform.PlatformScheduler;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.function.IntFunction;

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
        this.renderers = renderers;
    }

    static ModDashboardMaps create(MinecraftServer server, ModReactPlatform platform, IntFunction<MapRuntime.FrameAnchor> frameAnchors) {
        ModSamplerHost samplerHost = (ModSamplerHost) platform.samplerHost().orElseThrow();
        ModChunkHeatmapHost heatmaps = new ModChunkHeatmapHost(server, samplerHost.chunks());
        ModMapAnchorHost anchors = new ModMapAnchorHost(server, frameAnchors);
        ModOwnerCost ownerCost = platform.ownerCost();
        ChunkHeatmapRenderer.Settings settings = ChunkHeatmapRenderer.DEFAULT_SETTINGS;
        ModDashboardMaps maps = new ModDashboardMaps(heatmaps, List.of(
                new RendererReactMetrics(SamplerHosts::sampler),
                new ChunkCostHeatmap(heatmaps, anchors, settings),
                new ChunkLoadGenCostHeatmap(heatmaps, anchors, settings),
                new EntityPressureHeatmap(heatmaps, anchors, settings),
                new RedstoneActivityHeatmap(heatmaps, anchors, settings),
                new HopperThroughputHeatmap(heatmaps, anchors, settings),
                new PlayerImpactHeatmap(heatmaps, anchors, PlayerImpactHeatmap.DEFAULT_PLAYER_SETTINGS),
                new OwnerEventImpactListRenderer(anchors, () -> ownerCost, () -> 0)
        ));
        maps.schedule(platform.scheduler(), false);
        return maps;
    }

    public List<ReactRenderer> renderers() {
        return renderers;
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
