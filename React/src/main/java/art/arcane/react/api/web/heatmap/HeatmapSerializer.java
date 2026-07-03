package art.arcane.react.api.web.heatmap;

import java.util.List;

public class HeatmapSerializer {

    public HeatmapDto toDto(String id, String label, String world, int centerChunkX, int centerChunkZ, int radius, List<HeatmapCellDto> cells) {
        double min = 0.0;
        double max = 0.0;
        if (!cells.isEmpty()) {
            min = cells.get(0).score;
            max = cells.get(0).score;
            for (HeatmapCellDto cell : cells) {
                if (cell.score < min) {
                    min = cell.score;
                }
                if (cell.score > max) {
                    max = cell.score;
                }
            }
        }
        HeatmapDto dto = new HeatmapDto();
        dto.id = id;
        dto.label = label;
        dto.world = world;
        dto.centerChunkX = centerChunkX;
        dto.centerChunkZ = centerChunkZ;
        dto.radius = radius;
        dto.min = min;
        dto.max = max;
        dto.cells = cells.toArray(new HeatmapCellDto[0]);
        return dto;
    }
}
