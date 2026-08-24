package art.arcane.react.api.web.heatmap;

public interface ChunkGridExporter {

    String heatmapId();

    String heatmapLabel();

    double scoreChunk(HeatmapWorldRef world, int chunkX, int chunkZ);
}
