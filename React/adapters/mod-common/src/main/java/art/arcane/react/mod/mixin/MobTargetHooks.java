package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityActivityHost;
import art.arcane.react.mod.ModEntityTicker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobTargetHooks {
    @Inject(method = "setTarget", at = @At("HEAD"), require = 0)
    private void react$targeting(LivingEntity target, CallbackInfo callback) {
        ModEntityActivityHost.targeted((Mob) (Object) this, target);
        ModEntityTicker.poke((Mob) (Object) this);
        ModEntityTicker.poke(target);
    }
}
