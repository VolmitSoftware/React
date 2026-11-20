package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityTicker;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit's ItemMergeEvent: the item another one merges into is refreshed. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMergeHooks {
    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), require = 0)
    private static void react$merging(ItemEntity target, ItemStack targetStack, ItemEntity source, ItemStack sourceStack,
                                      CallbackInfo callback) {
        ModEntityTicker.poke(target);
    }
}
