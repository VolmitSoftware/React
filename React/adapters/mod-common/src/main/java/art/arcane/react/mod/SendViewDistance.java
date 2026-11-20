package art.arcane.react.mod;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;

/** The chunk map side of per-player send view distance; 1.20.1 has no per-player distance to cap. */
final class SendViewDistance {
    private SendViewDistance() {
    }

    /** True when React's send distance hook was applied to the chunk map. */
    static boolean supported(ChunkMap chunkMap) {
        return chunkMap instanceof SendDistanceAware;
    }

    /** The chunk map's distance for the player before the hook has reported it: the client's, within the server's. */
    static int natural(ChunkMap chunkMap, ServerPlayer player) {
        return Math.max(2, Math.min(player.requestedViewDistance(), VanillaApi.level(player).getServer().getPlayerList().getViewDistance()));
    }
}
