package art.arcane.react.api.web.heatmap;

import java.util.List;

public record HeatmapScan(String world, int centerChunkX, int centerChunkZ, List<HeatmapCellDto> cells) {}
