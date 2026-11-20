package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModBlockHost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * A flowing fluid changing its level or drying up, where Bukkit fires its fluid level change event. The first write
 * dries the block to air; the second writes the new fluid's own block.
 */
@Mixin(FlowingFluid.class)
public abstract class FluidLevelHooks {
    @Inject(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
                    ordinal = 0),
            cancellable = true, locals = LocalCapture.CAPTURE_FAILSOFT, require = 0)
    private void react$drying(Level level, BlockPos pos, FluidState fluidState, CallbackInfo callback, FluidState next) {
        if (!ModBlockHost.allowLevelChange(level, pos, Blocks.AIR.defaultBlockState())) {
            callback.cancel();
        }
    }

    @Inject(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
                    ordinal = 1),
            cancellable = true, locals = LocalCapture.CAPTURE_FAILSOFT, require = 0)
    private void react$levelChanging(Level level, BlockPos pos, FluidState fluidState, CallbackInfo callback, FluidState next) {
        if (!ModBlockHost.allowLevelChange(level, pos, next.createLegacyBlock())) {
            callback.cancel();
        }
    }
}
