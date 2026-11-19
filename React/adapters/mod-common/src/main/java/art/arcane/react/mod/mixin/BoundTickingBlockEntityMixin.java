package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.CostFrame;
import art.arcane.react.mod.cost.CostRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class BoundTickingBlockEntityMixin {
    @WrapOperation(method = "tick()V", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private void react$timeTicker(BlockEntityTicker<?> ticker, Level level, BlockPos pos, BlockState state, BlockEntity blockEntity,
                                  Operation<Void> original) {
        if (!CostRegistry.ticksActive) {
            original.call(ticker, level, pos, state, blockEntity);
            return;
        }
        CostBucket bucket = CostRegistry.blockEntityBucket(blockEntity);
        long start = CostFrame.enter();
        try {
            original.call(ticker, level, pos, state, blockEntity);
        } finally {
            CostFrame.exit(start, bucket);
        }
    }
}
