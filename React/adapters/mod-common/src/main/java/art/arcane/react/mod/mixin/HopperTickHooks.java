package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModHopperHost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Where Bukkit's agent hook decides whether a hopper block ticks at all. */
@Mixin(HopperBlockEntity.class)
public abstract class HopperTickHooks {
    @Inject(method = "pushItemsTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void react$ticking(Level level, BlockPos pos, BlockState state, HopperBlockEntity hopper, CallbackInfo callback) {
        if (ModHopperHost.skipsTick(level, pos)) {
            callback.cancel();
        }
    }
}
