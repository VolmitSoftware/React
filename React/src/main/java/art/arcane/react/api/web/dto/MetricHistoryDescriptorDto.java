package art.arcane.react.api.web.dto;

import art.arcane.react.api.sampler.Sampler;

public record MetricHistoryDescriptorDto(
    String id,
    String name,
    String suffix,
    long firstTimestampMs,
    long lastTimestampMs,
    boolean active,
    long intervalMs,
    long windowMs,
    Sampler.Quality quality,
    String unit
) {
}
