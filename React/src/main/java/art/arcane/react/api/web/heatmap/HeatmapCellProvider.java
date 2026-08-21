package art.arcane.react.api.web.heatmap;

import java.util.List;

public interface HeatmapCellProvider {

    List<HeatmapSummaryDto> summaries();

    HeatmapDto compute(String id, String world, Integer centerX, Integer centerZ, Integer radius);
}
