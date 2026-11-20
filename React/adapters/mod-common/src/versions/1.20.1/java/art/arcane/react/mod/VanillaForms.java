package art.arcane.react.mod;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/** The 1.20.1 forms of the vanilla calls whose shapes changed in 1.20.2 through 1.20.5. */
final class VanillaForms {
    private VanillaForms() {
    }

    static File serverDirectory(MinecraftServer server) {
        return server.getServerDirectory();
    }

    static LevelChunk fullChunk(ChunkHolder holder) {
        return holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).left().orElse(null);
    }

    static String language(ServerPlayer player) {
        return player instanceof ClientLanguage holder ? holder.react$language() : ClientLanguage.DEFAULT;
    }

    static int latency(ServerPlayer player) {
        return player.latency;
    }

    static long[] tickTimesNanos(MinecraftServer server) {
        return server.tickTimes;
    }

    static TextColor color(String text) {
        return TextColor.parseColor(text);
    }

    static CompletableFuture<LevelChunk> loadFullChunk(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, true)
                .thenApply((Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> result) ->
                        result.left().orElse(null) instanceof LevelChunk full ? full : null);
    }
}
