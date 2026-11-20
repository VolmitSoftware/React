package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModBlockRef;
import art.arcane.react.mod.ModSpawnHost;
import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.SamplerEventCounters.Counter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob spawner spawn, counted where the spawner plays its spawn effect after the mob was added. */
@Mixin(BaseSpawner.class)
public abstract class SpawnerHooks {
    @Inject(method = "serverTick", at = @At("HEAD"), require = 0)
    private void react$ticking(ServerLevel level, BlockPos pos, CallbackInfo callback) {
        ModSpawnHost.enter("SPAWNER");
    }

    @Inject(method = "serverTick", at = @At("RETURN"), require = 0)
    private void react$ticked(ServerLevel level, BlockPos pos, CallbackInfo callback) {
        ModSpawnHost.exit();
    }

    @Inject(method = "serverTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;levelEvent(ILnet/minecraft/core/BlockPos;I)V"),
            require = 0)
    private void react$spawned(ServerLevel level, BlockPos pos, CallbackInfo callback) {
        SamplerEventCounters counters = SamplerEventCounters.get();
        if (counters.isActive(Counter.SPAWNER_SPAWN)) {
            counters.count(Counter.SPAWNER_SPAWN, new ModBlockRef(level, pos));
        }
    }
}
