package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModBlockRef;
import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.SamplerEventCounters.Counter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block physics: every neighbour change a block reacts to on the server. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateHooks {
    @Inject(method = "handleNeighborChanged", at = @At("HEAD"), require = 0)
    private void react$neighborChanged(Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston,
                                       CallbackInfo callback) {
        SamplerEventCounters counters = SamplerEventCounters.get();
        if (level instanceof ServerLevel serverLevel && counters.isActive(Counter.BLOCK_PHYSICS)) {
            counters.count(Counter.BLOCK_PHYSICS, new ModBlockRef(serverLevel, pos));
        }
    }
}
