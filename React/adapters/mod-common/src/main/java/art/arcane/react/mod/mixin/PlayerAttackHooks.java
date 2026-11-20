package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Damage from a player or their projectile, where Bukkit fires EntityDamageByEntityEvent. A cancelled hit deals none.
 */
@Mixin(LivingEntity.class)
public abstract class PlayerAttackHooks {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$attack(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (ModPlayerHost.cancelsAttack(source)) {
            callback.setReturnValue(false);
        }
    }
}
