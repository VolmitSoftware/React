package art.arcane.react.platform;

import java.util.UUID;

public record ChunkRef(UUID worldId, int chunkX, int chunkZ) {
}
