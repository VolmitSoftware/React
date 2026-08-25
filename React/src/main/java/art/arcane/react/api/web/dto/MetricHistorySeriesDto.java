package art.arcane.react.api.web.dto;

public record MetricHistorySeriesDto(
    String id,
    String name,
    String suffix,
    Number[][] points
) {
}
