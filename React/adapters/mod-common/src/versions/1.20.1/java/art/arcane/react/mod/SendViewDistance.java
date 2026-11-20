package art.arcane.react.mod;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;

/** 1.20.1 tracks chunks for every player at the server's view distance, so there is no per-player distance to cap. */
final class SendViewDistance {
    private SendViewDistance() {
    }

    static boolean supported(ChunkMap chunkMap) {
        return false;
    }

    static int natural(ChunkMap chunkMap, ServerPlayer player) {
        return player.server.getPlayerList().getViewDistance();
    }
}
