package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.CostBucketHolder;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityType.class)
abstract class EntityTypeMixin implements CostBucketHolder {
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
