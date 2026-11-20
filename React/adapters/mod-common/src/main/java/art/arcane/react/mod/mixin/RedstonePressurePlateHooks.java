package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Pressure plates, weighted ones too, changing their signal, where CraftBukkit fires its block redstone event. */
@Mixin(BasePressurePlateBlock.class)
public abstract class RedstonePressurePlateHooks {
    @ModifyExpressionValue(method = "checkPressed", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/BasePressurePlateBlock;getSignalStrength(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I"))
    private int react$signal(int signal, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos,
                             @Local(argsOnly = true) BlockState state, @Local(argsOnly = true, ordinal = 0) int oldSignal) {
        return ModRedstoneHost.signal(level, pos, state, oldSignal, signal);
    }
}
