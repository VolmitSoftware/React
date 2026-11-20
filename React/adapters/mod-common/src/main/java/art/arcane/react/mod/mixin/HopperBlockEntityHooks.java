package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModItemHost;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityHooks {
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void react$pickup(Container container, ItemEntity item, CallbackInfoReturnable<Boolean> callback) {
        if (!ModItemHost.allowContainerPickup(container, item)) {
            callback.setReturnValue(false);
        }
    }
}
