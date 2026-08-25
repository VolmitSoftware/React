package art.arcane.react.api.web.dto;

public record MetricHistoryDescriptorDto(
    String id,
    String name,
    String suffix,
    long firstTimestampMs,
    long lastTimestampMs,
    boolean active
) {
}
