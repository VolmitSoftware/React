package art.arcane.react.platform;

import java.util.UUID;

/**
 * Where a player was when React's player index last saw them.
 */
public record PlayerPosition(UUID playerId, UUID worldId, double x, double y, double z) {
}
