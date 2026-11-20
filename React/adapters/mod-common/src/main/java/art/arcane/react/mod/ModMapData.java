package art.arcane.react.mod;

import art.arcane.react.api.rendering.MapRuntime;
import art.arcane.react.content.feature.AnimusMetrics;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.MapDataHost;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.platform.PlatformScheduler;
import net.minecraft.server.MinecraftServer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

/**
 * The data React's feature maps read on a mod server: chunk heatmaps, map anchors, integration metrics, Animus and
 * Iris biome shares. Each source reads only the mods that are installed.
 */
final class ModMapData implements MapDataHost {
    private final MinecraftServer server;
    private final ModOwnerCost ownerCost;
    private final ModChunkHeatmapHost heatmaps;
    private final ModMapAnchorHost anchors;
    private final ModIntegrationMetrics integrations;
    private final ModAnimusMetrics animus;
    private final ModIrisBiomes irisBiomes;
    private final Set<String> installed = new HashSet<>();
    private final AtomicBoolean irisBiomesActive = new AtomicBoolean();
    private final AtomicBoolean irisBiomesQueued = new AtomicBoolean();

    ModMapData(MinecraftServer server, ModChunkSamples samples, ModOwnerCost ownerCost, IntFunction<MapRuntime.FrameAnchor> frameAnchors) {
        this.server = server;
        this.ownerCost = ownerCost;
        this.heatmaps = new ModChunkHeatmapHost(server, samples);
        this.anchors = new ModMapAnchorHost(server, frameAnchors);
        List<ModIntegrationMetrics.Source> sources = ModIntegrationSources.installed();
        this.integrations = new ModIntegrationMetrics(server, sources);
        for (ModIntegrationMetrics.Source source : sources) {
            installed.add(source.id());
        }
        this.animus = ModAnimusMetrics.installed();
        if (animus != null) {
            installed.add(AnimusMetrics.SOURCE_ID);
        }
        this.irisBiomes = installed.contains("iris") ? new ModIrisBiomes(server) : null;
    }

    ModChunkHeatmapHost heatmaps() {
        return heatmaps;
    }

    ModMapAnchorHost anchors() {
        return anchors;
    }

    ModIntegrationMetrics integrations() {
        return integrations;
    }

    boolean isInstalled(String integrationId) {
        return integrationId != null && installed.contains(integrationId);
    }

    void schedule(PlatformScheduler scheduler) {
        if (integrations.any()) {
            integrations.refresh();
            integrations.schedule(scheduler);
        }
        if (animus != null) {
            animus.schedule(scheduler);
        }
    }

    @Override
    public boolean isViewerOnline(UUID viewerId) {
        return viewerId != null && server.getPlayerList().getPlayer(viewerId) != null;
    }

    @Override
    public AnimusMetrics.Snapshot animusSnapshot() {
        return animus == null ? AnimusMetrics.Snapshot.empty() : animus.snapshot();
    }

    @Override
    public EventMeasurement eventMeasurement() {
        return ownerCost;
    }

    @Override
    public void startIrisBiomes() {
        irisBiomesActive.set(true);
    }

    @Override
    public void stopIrisBiomes() {
        irisBiomesActive.set(false);
        if (irisBiomes != null) {
            server.execute(irisBiomes::clear);
        }
    }

    @Override
    public void tickIrisBiomes() {
        if (irisBiomes == null || !irisBiomesActive.get() || !irisBiomesQueued.compareAndSet(false, true)) {
            return;
        }
        server.execute(() -> {
            try {
                if (irisBiomesActive.get()) {
                    irisBiomes.sampleNow();
                }
            } finally {
                irisBiomesQueued.set(false);
            }
        });
    }

    @Override
    public Map<String, Long> irisBiomeBuckets(MapRenderTarget target) {
        return irisBiomes == null ? Map.of() : irisBiomes.buckets(target);
    }
}
