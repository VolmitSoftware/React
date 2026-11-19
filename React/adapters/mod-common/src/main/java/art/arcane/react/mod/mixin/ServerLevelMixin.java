package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.CostFrame;
import art.arcane.react.mod.cost.CostRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @WrapOperation(method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void react$timeEntity(Entity entity, Operation<Void> original) {
        react$time(entity, original);
    }

    @WrapOperation(method = "tickPassenger(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void react$timePassenger(Entity entity, Operation<Void> original) {
        react$time(entity, original);
    }

    @Unique
    private static void react$time(Entity entity, Operation<Void> original) {
        if (!CostRegistry.ticksActive) {
            original.call(entity);
            return;
        }
        CostBucket bucket = CostRegistry.entityBucket(entity);
        long start = CostFrame.enter();
        try {
            original.call(entity);
        } finally {
            CostFrame.exit(start, bucket);
        }
    }
}
