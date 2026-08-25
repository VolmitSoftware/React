package art.arcane.react.core.history;

import java.util.List;

public record HistoryQueryResult(
    long fromMs,
    long toMs,
    long resolutionMs,
    long throughSequence,
    long throughMs,
    List<HistoryQuerySeries> series
) {
}
