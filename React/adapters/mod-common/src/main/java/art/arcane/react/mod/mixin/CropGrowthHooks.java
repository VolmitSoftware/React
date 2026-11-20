package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModGrowthHost;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A crop ageing on its random tick, where Bukkit fires its block grow event. */
@Mixin({CropBlock.class, StemBlock.class, NetherWartBlock.class, CocoaBlock.class, SweetBerryBushBlock.class})
public abstract class CropGrowthHooks {
    @WrapOperation(method = "randomTick", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean react$grow(ServerLevel level, BlockPos pos, BlockState grown, int flags, Operation<Boolean> original) {
        return ModGrowthHost.allowGrow(level, pos, grown) && original.call(level, pos, grown, flags);
    }
}
