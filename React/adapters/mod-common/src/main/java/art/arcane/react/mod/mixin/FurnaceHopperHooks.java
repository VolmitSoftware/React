package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModFurnaceHost;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hopper moves into or out of a furnace or brewing stand, where Bukkit fires its inventory move event. */
@Mixin(HopperBlockEntity.class)
public abstract class FurnaceHopperHooks {
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), require = 0)
    private static void react$moving(Container from, Container to, ItemStack stack, Direction direction,
                                     CallbackInfoReturnable<ItemStack> callback) {
        if (from != null) {
            ModFurnaceHost.touch(to);
            ModFurnaceHost.touch(from);
        }
    }
}
