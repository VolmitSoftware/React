package art.arcane.react.model;

public record CircuitSnapshot(
    long circuitId,
    String worldId,
    String world,
    int events,
    int nodes,
    int x,
    int y,
    int z,
    int minX,
    int minY,
    int minZ,
    int maxX,
    int maxY,
    int maxZ,
    long blockedUntilMs
) {
}
