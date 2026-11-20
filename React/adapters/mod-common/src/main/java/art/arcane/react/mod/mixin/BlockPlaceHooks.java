package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModBlockHost;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player placing a block item, where Bukkit fires its block place event; a cancelled place fails the use. */
@Mixin(BlockItem.class)
public abstract class BlockPlaceHooks {
    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$placing(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> callback) {
        if (context.getPlayer() != null && !ModBlockHost.allowPlace(context.getLevel(), context.getClickedPos(), state, context.getPlayer())) {
            callback.setReturnValue(false);
        }
    }
}
