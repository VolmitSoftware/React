package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModSpawnReason;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobSpawnReasonHooks {
    @Inject(method = "finalizeSpawn", at = @At("HEAD"), require = 0)
    private void react$finalizing(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                  @Nullable SpawnGroupData groupData, CallbackInfoReturnable<SpawnGroupData> callback) {
        ModSpawnReason.record(this, reason);
    }
}
