package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModGrowthHost;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A plant growing a new block on its random tick, such as sugar cane, cactus or kelp. */
@Mixin({SugarCaneBlock.class, CactusBlock.class, GrowingPlantHeadBlock.class})
public abstract class PlantGrowthHooks {
    @WrapOperation(method = "randomTick", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean react$grow(ServerLevel level, BlockPos pos, BlockState grown, Operation<Boolean> original) {
        return ModGrowthHost.allowGrow(level, pos, grown) && original.call(level, pos, grown);
    }
}
