package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModMapHooks;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrame.class)
public abstract class ItemFrameHooks {
    @Inject(method = "setItem(Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("TAIL"), require = 0)
    private void react$itemChanged(ItemStack stack, boolean updateNeighbours, CallbackInfo callback) {
        ModMapHooks.frameChanged((ItemFrame) (Object) this);
    }

    @Inject(method = "setRotation(IZ)V", at = @At("TAIL"), require = 0)
    private void react$rotated(int rotation, boolean updateNeighbours, CallbackInfo callback) {
        ModMapHooks.frameChanged((ItemFrame) (Object) this);
    }
}
