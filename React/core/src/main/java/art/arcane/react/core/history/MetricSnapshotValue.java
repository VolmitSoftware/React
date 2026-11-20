package art.arcane.react.core.history;

import art.arcane.react.api.sampler.Sampler;

public record MetricSnapshotValue(
    String id,
    String name,
    String suffix,
    double value,
    String display,
    boolean available,
    long sampledAtMs,
    Sampler.Metadata metadata,
    String unavailableReason,
    Sampler.Diagnostics diagnostics
) {
  public MetricSnapshotValue {
    metadata = metadata == null ? Sampler.Metadata.UNKNOWN : metadata;
    diagnostics = diagnostics == null ? Sampler.Diagnostics.EMPTY : diagnostics;
    unavailableReason = available ? "" : unavailableReason == null || unavailableReason.isBlank() ? "source-unavailable" : unavailableReason;
  }
}
