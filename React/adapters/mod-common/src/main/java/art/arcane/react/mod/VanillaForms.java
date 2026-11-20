package art.arcane.react.mod;

import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/** Vanilla calls whose forms changed after 1.20.1; the 1.20.1 overlay carries the older forms. */
final class VanillaForms {
    private VanillaForms() {
    }

    static File serverDirectory(MinecraftServer server) {
        return server.getServerDirectory().toFile();
    }

    static LevelChunk fullChunk(ChunkHolder holder) {
        return holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).orElse(null);
    }

    static String language(ServerPlayer player) {
        return player.clientInformation().language();
    }

    static int latency(ServerPlayer player) {
        return player.connection.latency();
    }

    static long[] tickTimesNanos(MinecraftServer server) {
        return server.getTickTimesNanos();
    }

    static TextColor color(String text) {
        return TextColor.parseColor(text).result().orElse(null);
    }

    static CompletableFuture<LevelChunk> loadFullChunk(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, true)
                .thenApply((ChunkResult<ChunkAccess> result) -> result.orElse(null) instanceof LevelChunk full ? full : null);
    }
}
