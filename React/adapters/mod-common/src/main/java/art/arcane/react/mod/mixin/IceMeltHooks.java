package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModGrowthHost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ice about to melt, where Bukkit fires its block fade event. */
@Mixin(IceBlock.class)
public abstract class IceMeltHooks {
    @Inject(method = "melt", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$melting(BlockState state, Level level, BlockPos pos, CallbackInfo callback) {
        if (!ModGrowthHost.allowFade(level, pos)) {
            callback.cancel();
        }
    }
}
