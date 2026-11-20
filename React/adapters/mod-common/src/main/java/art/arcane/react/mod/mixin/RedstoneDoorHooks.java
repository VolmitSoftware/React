package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Doors opened or closed by power, where CraftBukkit fires its block redstone event; a held change leaves the block as it was, as on Bukkit. */
@Mixin(DoorBlock.class)
public abstract class RedstoneDoorHooks {
    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$switching(CallbackInfo callback, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) Level level,
                                 @Local(argsOnly = true, ordinal = 0) BlockPos pos, @Local(argsOnly = true) Block block) {
        if (!ModRedstoneHost.isListening()) {
            return;
        }
        boolean signal = level.hasNeighborSignal(pos)
                || level.hasNeighborSignal(pos.relative(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));
        if (!((Block) (Object) this).defaultBlockState().is(block) && signal != state.getValue(DoorBlock.POWERED)
                && ModRedstoneHost.held(level, pos, state, signal)) {
            callback.cancel();
        }
    }
}
