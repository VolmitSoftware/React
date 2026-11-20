package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModBlockHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A flowing fluid changing its level or drying up, where Bukkit fires its fluid level change event. At both writes the
 * block state parameter already holds the new state.
 */
@Mixin(FlowingFluid.class)
public abstract class FluidLevelHooks {
    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
            cancellable = true, require = 0)
    private void react$levelChanging(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState, CallbackInfo callback) {
        if (!ModBlockHost.allowLevelChange(level, pos, blockState)) {
            callback.cancel();
        }
    }
}
