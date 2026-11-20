package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModGrowthHost;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fire at the points where Bukkit fires its events: burning out (fade), spreading to a nearby block (spread) and
 * burning a flammable neighbour away (burn). Newer versions spread with setBlockAndUpdate, 1.21.1 with setBlock.
 */
@Mixin(FireBlock.class)
public abstract class FireGrowthHooks {
    @WrapOperation(method = "tick", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean react$burnOut(ServerLevel level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        return ModGrowthHost.allowFade(level, pos) && original.call(level, pos, moving);
    }

    @WrapOperation(method = "tick", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean react$spread(ServerLevel level, BlockPos pos, BlockState fire, Operation<Boolean> original,
                                 @Local(argsOnly = true) BlockPos origin) {
        return ModGrowthHost.allowSpread(level, pos, origin, fire) && original.call(level, pos, fire);
    }

    @WrapOperation(method = "tick", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean react$spreadOrAge(ServerLevel level, BlockPos pos, BlockState fire, int flags, Operation<Boolean> original,
                                      @Local(argsOnly = true) BlockPos origin) {
        if (!pos.equals(origin) && !ModGrowthHost.allowSpread(level, pos, origin, fire)) {
            return false;
        }
        return original.call(level, pos, fire, flags);
    }

    @WrapOperation(method = "checkBurnOut", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean react$burnIntoFire(Level level, BlockPos pos, BlockState fire, Operation<Boolean> original) {
        return ModGrowthHost.allowBurn(level, pos) && original.call(level, pos, fire);
    }

    @WrapOperation(method = "checkBurnOut", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean react$burnIntoFireLegacy(Level level, BlockPos pos, BlockState fire, int flags, Operation<Boolean> original) {
        return ModGrowthHost.allowBurn(level, pos) && original.call(level, pos, fire, flags);
    }

    @WrapOperation(method = "checkBurnOut", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean react$burnAway(Level level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        return ModGrowthHost.allowBurn(level, pos) && original.call(level, pos, moving);
    }
}
