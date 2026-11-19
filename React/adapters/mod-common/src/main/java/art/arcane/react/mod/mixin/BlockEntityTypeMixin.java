package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.CostBucketHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntityType.class)
abstract class BlockEntityTypeMixin implements CostBucketHolder {
    @Unique
    private volatile CostBucket react$costBucket;

    @Override
    public CostBucket react$costBucket() {
        return react$costBucket;
    }

    @Override
    public void react$costBucket(CostBucket bucket) {
        react$costBucket = bucket;
    }
}
