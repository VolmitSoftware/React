package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityTicker;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit's EntityRegainHealthEvent, as an entity tick trigger. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealHooks {
    @Inject(method = "heal", at = @At("HEAD"), require = 0)
    private void react$healing(float amount, CallbackInfo callback) {
        ModEntityTicker.poke((LivingEntity) (Object) this);
    }
}
