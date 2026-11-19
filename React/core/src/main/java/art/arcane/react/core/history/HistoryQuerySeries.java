package art.arcane.react.core.history;

import java.util.List;

public record HistoryQuerySeries(
    String id,
    String name,
    String suffix,
    List<HistoryPoint> points
) {
}
