package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModDropHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDropHooks {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), require = 0)
    private void react$beginDeathDrops(ServerLevel level, DamageSource source, CallbackInfo callback) {
        ModDropHost.beginDeath((LivingEntity) (Object) this, source);
    }

    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"), require = 0)
    private void react$endDeathDrops(ServerLevel level, DamageSource source, CallbackInfo callback) {
        ModDropHost.endDeath((LivingEntity) (Object) this);
    }
}
