package art.arcane.react.api.web.heatmap;

public interface HeatmapChunkSampler {

    HeatmapScan scan(ChunkGridExporter exporter, String requestedWorld, Integer centerX, Integer centerZ, int radius);
}
