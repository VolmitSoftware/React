package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModReactWorld;
import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.SamplerEventCounters.Counter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelHooks {
    @Inject(method = "unload", at = @At("HEAD"), require = 0)
    private void react$unloading(LevelChunk chunk, CallbackInfo callback) {
        SamplerEventCounters.get().count(Counter.CHUNK_UNLOAD, chunk);
    }

    @Inject(method = "save", at = @At("TAIL"), require = 0)
    private void react$saved(ProgressListener progressListener, boolean flush, boolean noSave, CallbackInfo callback) {
        SamplerEventCounters counters = SamplerEventCounters.get();
        if (!noSave && counters.isActive(Counter.WORLD_SAVE)) {
            counters.count(Counter.WORLD_SAVE, ModReactWorld.idOf((ServerLevel) (Object) this));
        }
    }

    @Inject(method = "addFreshEntity", at = @At("HEAD"), require = 0)
    private void react$spawning(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        SamplerEventCounters.get().count(Counter.ENTITY_SPAWN, entity);
    }
}
