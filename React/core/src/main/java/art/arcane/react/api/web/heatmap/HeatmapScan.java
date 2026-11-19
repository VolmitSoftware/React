package art.arcane.react.api.web.heatmap;

import java.util.List;

public record HeatmapScan(
    HeatmapWorldRef world,
    HeatmapViewportPlanner.HeatmapViewportPlan viewport,
    long capturedAtMs,
    List<HeatmapCellDto> cells
) {}
