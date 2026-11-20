package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModItemHost;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuHooks {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$clicked(int slotIndex, int button, ContainerInput input, Player player, CallbackInfo callback) {
        if (!ModItemHost.allowSlotClick((AbstractContainerMenu) (Object) this, slotIndex, player)) {
            callback.cancel();
        }
    }
}
