package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.DefaultRedstoneWireEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Wire power changes, where CraftBukkit fires its block redstone event and uses the power listeners settle on. */
@Mixin(DefaultRedstoneWireEvaluator.class)
public abstract class RedstoneSignalWireHooks {
    @ModifyVariable(method = "updatePowerStrength", at = @At(value = "STORE", ordinal = 0), ordinal = 0, require = 0)
    private int react$signal(int targetStrength, Level level, BlockPos pos, BlockState state) {
        return ModRedstoneHost.signal(level, pos, state, state.getValue(RedstoneWireBlock.POWER), targetStrength);
    }
}
