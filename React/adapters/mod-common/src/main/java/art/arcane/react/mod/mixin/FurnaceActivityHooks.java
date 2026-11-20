package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModFurnaceHost;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player opening a furnace or brewing stand, where Bukkit fires its inventory open event. */
@Mixin(BaseContainerBlockEntity.class)
public abstract class FurnaceActivityHooks {
    @Inject(method = "createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
            at = @At("RETURN"), require = 0)
    private void react$opened(int containerId, Inventory inventory, Player player, CallbackInfoReturnable<AbstractContainerMenu> callback) {
        if (callback.getReturnValue() != null) {
            ModFurnaceHost.touch(this);
        }
    }
}
