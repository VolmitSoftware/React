package art.arcane.react.mod;

import art.arcane.react.mod.mixin.ChunkMapViewAccess;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;

/** The chunk map calls behind per-player send view distance; 1.20.1 has no per-player distance to cap. */
final class SendViewDistance {
    private SendViewDistance() {
    }

    static boolean supported(ChunkMap chunkMap) {
        return chunkMap instanceof ChunkMapViewAccess;
    }

    static int natural(ChunkMap chunkMap, ServerPlayer player) {
        return ((ChunkMapViewAccess) chunkMap).react$playerViewDistance(player);
    }

    static void refresh(ChunkMap chunkMap, ServerPlayer player) {
        ((ChunkMapViewAccess) chunkMap).react$updateChunkTracking(player);
    }
}
