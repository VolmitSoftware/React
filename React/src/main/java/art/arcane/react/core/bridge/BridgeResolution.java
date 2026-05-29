package art.arcane.react.core.bridge;

public record BridgeResolution(
    String logicalId,
    BridgeKind kind,
    boolean available,
    String resolvedClassName,
    String resolvedMemberName,
    String resolutionSummary,
    String failureReason
) {}
