package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModBlockHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chunk and world unloads for the block host's loaded-chunk index and listeners. */
@Mixin(ServerLevel.class)
public abstract class ChunkUnloadHooks {
    @Inject(method = "unload", at = @At("HEAD"), require = 0)
    private void react$chunkUnloading(LevelChunk chunk, CallbackInfo callback) {
        ModBlockHost.chunkUnloading((ServerLevel) (Object) this, chunk);
    }

    @Inject(method = "close", at = @At("HEAD"), require = 0)
    private void react$worldUnloading(CallbackInfo callback) {
        ModBlockHost.worldUnloading((ServerLevel) (Object) this);
    }
}
