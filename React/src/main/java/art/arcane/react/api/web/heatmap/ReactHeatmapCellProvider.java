package art.arcane.react.api.web.heatmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

public class ReactHeatmapCellProvider implements HeatmapCellProvider {

    private final Supplier<Collection<ChunkGridExporter>> exporters;
    private final HeatmapChunkSampler sampler;
    private final HeatmapSerializer serializer;
    private final HeatmapViewportPlanner viewportPlanner;
    private final int defaultRadius;
    private final int maxRadius;

    public ReactHeatmapCellProvider(
        Supplier<Collection<ChunkGridExporter>> exporters,
        HeatmapChunkSampler sampler,
        HeatmapSerializer serializer,
        int defaultRadius,
        int maxRadius
    ) {
        this.exporters = exporters;
        this.sampler = sampler;
        this.serializer = serializer;
        viewportPlanner = new HeatmapViewportPlanner();
        if (maxRadius < 1 || maxRadius > HeatmapViewportPlanner.MAX_RADIUS) {
            throw new IllegalArgumentException(
                "Maximum heatmap radius must be between 1 and " + HeatmapViewportPlanner.MAX_RADIUS
            );
        }
        if (defaultRadius < 1 || defaultRadius > maxRadius) {
            throw new IllegalArgumentException("Default heatmap radius must be within the configured radius range");
        }
        this.defaultRadius = defaultRadius;
        this.maxRadius = maxRadius;
    }

    @Override
    public List<HeatmapSummaryDto> summaries() {
        Collection<ChunkGridExporter> all = exporters.get();
        if (all == null) {
            return List.of();
        }
        Map<String, HeatmapSummaryDto> summariesById = new TreeMap<>();
        for (ChunkGridExporter e : all) {
            if (e == null || e.heatmapId() == null || e.heatmapId().isBlank()) {
                continue;
            }
            summariesById.putIfAbsent(
                e.heatmapId(),
                new HeatmapSummaryDto(e.heatmapId(), e.heatmapLabel())
            );
        }
        return new ArrayList<>(summariesById.values());
    }

    @Override
    public HeatmapDto compute(String id, String world, Integer centerX, Integer centerZ, Integer radius) {
        Collection<ChunkGridExporter> all = exporters.get();
        ChunkGridExporter exporter = null;
        if (all != null) {
            for (ChunkGridExporter e : all) {
                if (e != null && e.heatmapId() != null && e.heatmapId().equals(id)) {
                    exporter = e;
                    break;
                }
            }
        }
        if (exporter == null) {
            return null;
        }
        int r = radius == null ? defaultRadius : radius;
        if (r < 1 || r > maxRadius) {
            throw new IllegalArgumentException("Heatmap radius must be between 1 and " + maxRadius);
        }
        HeatmapScan scan = sampler.scan(exporter, world, centerX, centerZ, r);
        if (scan == null) {
            int fallbackCenterX = centerX == null ? 0 : centerX;
            int fallbackCenterZ = centerZ == null ? 0 : centerZ;
            HeatmapWorldRef fallbackWorld = new HeatmapWorldRef(
                null,
                world == null ? "" : world,
                "",
                0,
                0,
                0D,
                0D,
                0D
            );
            scan = new HeatmapScan(
                fallbackWorld,
                viewportPlanner.plan(fallbackCenterX, fallbackCenterZ, r),
                System.currentTimeMillis(),
                List.of()
            );
        }
        return serializer.toDto(id, exporter.heatmapLabel(), scan);
    }
}
