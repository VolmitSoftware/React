package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityEventHost;
import art.arcane.react.mod.ModMobStackHost;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bukkit's EntityDeathEvent and EntityDamageEvent for living entities. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHooks {
    @Shadow
    protected boolean dead;

    @Unique
    private boolean react$dying;

    @Inject(method = "die", at = @At("HEAD"), require = 0)
    private void react$dying(DamageSource source, CallbackInfo callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        react$dying = !entity.isRemoved() && !dead;
    }

    @Inject(method = "die", at = @At("TAIL"), require = 0)
    private void react$died(DamageSource source, CallbackInfo callback) {
        if (react$dying && dead) {
            react$dying = false;
            ModMobStackHost.died((LivingEntity) (Object) this);
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), require = 0)
    private void react$hurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        ModEntityEventHost.damaged((LivingEntity) (Object) this, source);
    }
}
