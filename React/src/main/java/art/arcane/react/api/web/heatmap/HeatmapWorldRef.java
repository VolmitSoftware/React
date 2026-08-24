package art.arcane.react.api.web.heatmap;

import java.util.UUID;

public record HeatmapWorldRef(
    UUID worldId,
    String worldKey,
    String worldName,
    int spawnChunkX,
    int spawnChunkZ
) {
}
