package art.arcane.react.api.web.heatmap;

import org.bukkit.Chunk;

public interface ChunkGridExporter {

    String heatmapId();

    String heatmapLabel();

    double scoreChunk(Chunk chunk);
}
