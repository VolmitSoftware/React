package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.ExperimentalRedstoneWireEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wire power changes under the experimental redstone rules, where CraftBukkit fires its block redstone event. */
@Mixin(ExperimentalRedstoneWireEvaluator.class)
public abstract class RedstoneExperimentalWireHooks {
    @ModifyExpressionValue(method = "updatePowerStrength", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/redstone/ExperimentalRedstoneWireEvaluator;unpackPower(I)I"))
    private int react$signal(int power, @Local(argsOnly = true) Level level, @Local(ordinal = 1) BlockPos pos) {
        if (!ModRedstoneHost.isListening()) {
            return power;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedStoneWireBlock) || state.getValue(RedStoneWireBlock.POWER) == power) {
            return power;
        }
        return ModRedstoneHost.signal(level, pos, state, state.getValue(RedStoneWireBlock.POWER), power);
    }
}
