package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModChunkHost;
import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.SamplerEventCounters.Counter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A chunk built from a proto chunk was generated (or upgraded) this session; one unwrapped from disk was loaded.
 * Loading runs from {@code runPostLoad} to {@code setUnsavedListener}, both called once per chunk.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkHooks {
    @Unique
    private boolean react$generated;

    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
            at = @At("RETURN"), require = 0)
    private void react$fromProto(ServerLevel level, ProtoChunk protoChunk, LevelChunk.PostLoadProcessor postLoad, CallbackInfo callback) {
        react$generated = true;
    }

    @Inject(method = "runPostLoad", at = @At("HEAD"), require = 0)
    private void react$loading(CallbackInfo callback) {
        SamplerEventCounters counters = SamplerEventCounters.get();
        counters.count(react$generated ? Counter.CHUNK_GENERATE_BEGIN : Counter.CHUNK_LOAD_BEGIN, this);
    }

    @Inject(method = "setUnsavedListener", at = @At("TAIL"), require = 0)
    private void react$loaded(CallbackInfo callback) {
        SamplerEventCounters counters = SamplerEventCounters.get();
        ModChunkHost.chunkLoaded((LevelChunk) (Object) this);
        counters.count(Counter.CHUNK_LOAD, this);
        if (react$generated) {
            counters.count(Counter.CHUNK_GENERATE, this);
            counters.count(Counter.CHUNK_GENERATE_END, this);
        } else {
            counters.count(Counter.CHUNK_LOAD_END, this);
        }
    }
}
