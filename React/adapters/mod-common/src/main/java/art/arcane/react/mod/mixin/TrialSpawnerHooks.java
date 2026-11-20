package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModBlockRef;
import art.arcane.react.mod.ModSpawnHost;
import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.SamplerEventCounters.Counter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerHooks {
    @Inject(method = "spawnMob", at = @At("HEAD"), require = 0)
    private void react$spawning(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Optional<UUID>> callback) {
        ModSpawnHost.enter("TRIAL_SPAWNER");
    }

    @Inject(method = "spawnMob", at = @At("RETURN"), require = 0)
    private void react$spawned(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Optional<UUID>> callback) {
        ModSpawnHost.exit();
        Optional<UUID> spawned = callback.getReturnValue();
        SamplerEventCounters counters = SamplerEventCounters.get();
        if (spawned != null && spawned.isPresent() && counters.isActive(Counter.SPAWNER_SPAWN)) {
            counters.count(Counter.SPAWNER_SPAWN, new ModBlockRef(level, pos));
        }
    }
}
