package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModGrowthHost;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vines spreading into a neighbouring block on their random tick. */
@Mixin(VineBlock.class)
public abstract class VineSpreadHooks {
    @WrapOperation(method = "randomTick", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean react$spread(ServerLevel level, BlockPos pos, BlockState vine, int flags, Operation<Boolean> original,
                                 @Local(argsOnly = true) BlockPos origin) {
        if (!pos.equals(origin) && !ModGrowthHost.allowSpread(level, pos, origin, vine)) {
            return false;
        }
        return original.call(level, pos, vine, flags);
    }
}
