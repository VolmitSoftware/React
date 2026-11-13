package art.arcane.react.api.web.dto;

public record MetricRangeDto(
    String range,
    long stepMs,
    long fromMs,
    long toMs,
    MetricRangeSeriesDto[] series
) {
}
