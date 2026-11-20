package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityActivityHost;
import art.arcane.react.mod.ModEntityTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageHooks {
    @Inject(method = "hurtServer", at = @At("RETURN"), require = 0)
    private void react$damaged(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) {
            ModEntityActivityHost.damaged((LivingEntity) (Object) this);
            ModEntityTicker.poke((LivingEntity) (Object) this);
        }
    }
}
