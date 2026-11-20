package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModGrowthHost;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.SpreadingSnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Grass and mycelium spreading to nearby dirt (spread), or dying back to dirt (fade). */
@Mixin(SpreadingSnowyBlock.class)
public abstract class GrassSpreadHooks {
    @WrapOperation(method = "randomTick", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean react$spread(ServerLevel level, BlockPos pos, BlockState changed, Operation<Boolean> original,
                                 @Local(argsOnly = true) BlockPos origin) {
        boolean allowed = pos.equals(origin) ? ModGrowthHost.allowFade(level, pos) : ModGrowthHost.allowSpread(level, pos, origin, changed);
        return allowed && original.call(level, pos, changed);
    }
}
