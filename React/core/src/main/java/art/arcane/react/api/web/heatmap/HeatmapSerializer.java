package art.arcane.react.api.web.heatmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HeatmapSerializer {

    public HeatmapDto toDto(String id, String label, HeatmapScan scan) {
        HeatmapWorldRef world = scan.world();
        HeatmapViewportPlanner.HeatmapViewportPlan viewport = scan.viewport();
        List<HeatmapCellDto> cells = scan.cells();
        HeatmapDto dto = new HeatmapDto();
        dto.id = id;
        dto.label = label;
        dto.world = world.worldKey() == null ? "" : world.worldKey();
        dto.centerChunkX = viewport.centerChunkX();
        dto.centerChunkZ = viewport.centerChunkZ();
        dto.radius = viewport.radius();
        dto.originChunkX = viewport.originChunkX();
        dto.originChunkZ = viewport.originChunkZ();
        dto.width = viewport.width();
        dto.height = viewport.height();
        dto.cellSizeChunks = viewport.cellSizeChunks();
        dto.capturedAtMs = scan.capturedAtMs();
        dto.spawnChunkX = world.spawnChunkX();
        dto.spawnChunkZ = world.spawnChunkZ();
        dto.worldBorder = new HeatmapWorldBorderDto(
            world.borderCenterBlockX(),
            world.borderCenterBlockZ(),
            world.borderSizeBlocks()
        );
        dto.min = 0D;
        dto.max = robustPositiveScale(cells);
        dto.cells = cells.toArray(new HeatmapCellDto[0]);
        return dto;
    }

    private double robustPositiveScale(List<HeatmapCellDto> cells) {
        List<Double> positivePeaks = new ArrayList<>(cells.size());
        for (HeatmapCellDto cell : cells) {
            if (Double.isFinite(cell.score) && cell.score > 0D) {
                positivePeaks.add(cell.score);
            }
        }
        if (positivePeaks.isEmpty()) {
            return 0D;
        }
        Collections.sort(positivePeaks);
        int percentileIndex = Math.max(0, (int) Math.ceil(positivePeaks.size() * 0.95D) - 1);
        return positivePeaks.get(percentileIndex);
    }
}
