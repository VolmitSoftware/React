package art.arcane.react.core.history;

public record MetricSnapshotValue(
    String id,
    String name,
    String suffix,
    double value,
    String display,
    boolean available
) {
}
