package art.arcane.react.api.web.dto;

public record PlayerTeleportDto(
    String playerId,
    String playerName,
    String status,
    String worldKey,
    int blockX,
    int blockZ
) {
}
