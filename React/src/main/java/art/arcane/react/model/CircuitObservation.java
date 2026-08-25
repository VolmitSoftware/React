package art.arcane.react.model;

public record CircuitObservation(
    long circuitId,
    boolean blocked,
    long blockedUntilMs
) {
}
