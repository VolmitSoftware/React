package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player finishing an item, where Bukkit fires PlayerItemConsumeEvent. A cancelled one keeps the item.
 */
@Mixin(LivingEntity.class)
public abstract class PlayerConsumeHooks {
    @Inject(method = "completeUsingItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$consume(CallbackInfo callback) {
        if ((Object) this instanceof ServerPlayer player && ModPlayerHost.cancelsConsume(player)) {
            player.stopUsingItem();
            ModPlayerHost.resync(player);
            callback.cancel();
        }
    }
}
