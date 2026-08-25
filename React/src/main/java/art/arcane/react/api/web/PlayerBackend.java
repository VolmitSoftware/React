package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.OnlinePlayerDto;

import java.util.List;
import java.util.UUID;

public interface PlayerBackend {

    List<OnlinePlayerDto> list();

    TeleportResult queueTeleport(UUID playerId, String worldKey, int blockX, int blockZ);

    enum TeleportStatus {
        QUEUED,
        PLAYER_OFFLINE,
        WORLD_UNAVAILABLE,
        OUTSIDE_BORDER,
        REJECTED
    }

    record TeleportResult(TeleportStatus status, String playerName) {
    }
}
