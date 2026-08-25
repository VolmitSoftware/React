package art.arcane.react.core.incident;

public record IncidentEvidence(
    String metricId,
    String label,
    boolean available,
    double value,
    String display,
    double pressure,
    double weight,
    double scorePoints,
    double minimum,
    double maximum
) {
}
