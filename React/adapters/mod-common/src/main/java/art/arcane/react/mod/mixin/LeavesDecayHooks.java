package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModGrowthHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves about to decay on their random tick, where Bukkit fires its leaves decay event. */
@Mixin(LeavesBlock.class)
public abstract class LeavesDecayHooks {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$decaying(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (!state.getValue(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.DISTANCE) == LeavesBlock.DECAY_DISTANCE
                && !ModGrowthHost.allowLeavesDecay(level, pos)) {
            callback.cancel();
        }
    }
}
