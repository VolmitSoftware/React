package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Trapdoors opened or closed by power, where CraftBukkit fires its block redstone event; a held change leaves the block as it was, as on Bukkit. */
@Mixin(TrapDoorBlock.class)
public abstract class RedstoneTrapDoorHooks {
    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$switching(CallbackInfo callback, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) Level level,
                                 @Local(argsOnly = true, ordinal = 0) BlockPos pos, @Local(argsOnly = true) Block block) {
        if (!ModRedstoneHost.isListening()) {
            return;
        }
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != state.getValue(TrapDoorBlock.POWERED) && ModRedstoneHost.held(level, pos, state, signal)) {
            callback.cancel();
        }
    }
}
