package art.arcane.react.api.web.dto;

public record PluginApiMetricDto(
    String id,
    String samplerId,
    String displayName,
    String sourceType,
    boolean available,
    String availabilityReason,
    long sampledAtMs,
    long sampleDurationMs,
    long acceptedSamples,
    long failedSamples,
    boolean quarantined
) {
}
