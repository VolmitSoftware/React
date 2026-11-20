package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModHopperHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperMoveHooks {
    @Inject(method = "ejectItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;removeItem(II)Lnet/minecraft/world/item/ItemStack;"),
            cancellable = true, require = 0)
    private static void react$push(Level level, BlockPos pos, HopperBlockEntity hopper, CallbackInfoReturnable<Boolean> callback) {
        if (!ModHopperHost.allowPush(level, pos)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "tryTakeInItemFromSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Container;removeItem(II)Lnet/minecraft/world/item/ItemStack;"),
            cancellable = true, require = 0)
    private static void react$pull(Hopper hopper, Container container, int slot, Direction direction,
                                   CallbackInfoReturnable<Boolean> callback) {
        Level level = hopper instanceof BlockEntity block ? block.getLevel() : hopper instanceof Entity entity ? entity.level() : null;
        if (!ModHopperHost.allowPull(level, hopper, container)) {
            callback.setReturnValue(false);
        }
    }
}
