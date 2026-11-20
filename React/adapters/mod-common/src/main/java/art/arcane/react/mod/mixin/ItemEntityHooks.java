package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModItemHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public abstract class ItemEntityHooks {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$touched(Player player, CallbackInfo callback) {
        if (!ModItemHost.allowPlayerPickup((ItemEntity) (Object) this, player)) {
            callback.cancel();
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$hurt(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (!ModItemHost.allowDamage((ItemEntity) (Object) this, source)) {
            callback.setReturnValue(false);
        }
    }
}
