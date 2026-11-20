package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModSpawnHost;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player dropping an item, where Bukkit fires its player drop item event. */
@Mixin(ServerPlayer.class)
public abstract class PlayerDropHooks {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void react$dropping(ItemStack stack, boolean dropAround, boolean includeThrower, CallbackInfoReturnable<ItemEntity> callback) {
        if (!ModSpawnHost.allowDrop((ServerPlayer) (Object) this, stack)) {
            callback.setReturnValue(null);
        }
    }
}
