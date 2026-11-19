package art.arcane.react.api.web.dto;

public record MetricHistoryPageDto(
    long requestedFromMs,
    long requestedToMs,
    long pageFromMs,
    long pageToMs,
    long actualResolutionMs,
    long throughSequence,
    long throughMs,
    String nextCursor,
    MetricHistorySeriesDto[] series
) {
}
