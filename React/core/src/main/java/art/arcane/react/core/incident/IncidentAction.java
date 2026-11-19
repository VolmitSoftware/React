package art.arcane.react.core.incident;

public record IncidentAction(
    String id,
    String label,
    String status,
    String detail,
    long occurredAtMs
) {
}
