package art.arcane.react.api.web.heatmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class ReactHeatmapCellProvider implements HeatmapCellProvider {

    private final Supplier<Collection<ChunkGridExporter>> exporters;
    private final HeatmapChunkSampler sampler;
    private final HeatmapSerializer serializer;
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
        this.defaultRadius = defaultRadius;
        this.maxRadius = maxRadius;
    }

    @Override
    public List<HeatmapSummaryDto> summaries() {
        Collection<ChunkGridExporter> all = exporters.get();
        if (all == null) {
            return List.of();
        }
        List<HeatmapSummaryDto> result = new ArrayList<>();
        for (ChunkGridExporter e : all) {
            result.add(new HeatmapSummaryDto(e.heatmapId(), e.heatmapLabel()));
        }
        return result;
    }

    @Override
    public HeatmapDto compute(String id, String world, Integer centerX, Integer centerZ, Integer radius) {
        Collection<ChunkGridExporter> all = exporters.get();
        ChunkGridExporter exporter = null;
        if (all != null) {
            for (ChunkGridExporter e : all) {
                if (e.heatmapId().equals(id)) {
                    exporter = e;
                    break;
                }
            }
        }
        if (exporter == null) {
            return null;
        }
        int r = radius == null ? defaultRadius : Math.max(1, Math.min(maxRadius, radius));
        HeatmapScan scan = sampler.scan(exporter, world, centerX, centerZ, r);
        if (scan == null) {
            return serializer.toDto(
                id,
                exporter.heatmapLabel(),
                world == null ? "" : world,
                centerX == null ? 0 : centerX,
                centerZ == null ? 0 : centerZ,
                r,
                List.of()
            );
        }
        return serializer.toDto(id, exporter.heatmapLabel(), scan.world(), scan.centerChunkX(), scan.centerChunkZ(), r, scan.cells());
    }
}
