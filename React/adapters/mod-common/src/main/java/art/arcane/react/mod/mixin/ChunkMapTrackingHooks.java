package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityRanges;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapTrackingHooks {
    @Unique
    private Entity react$trackingEntity;

    @Inject(method = "addEntity", at = @At("HEAD"), require = 0)
    private void react$trackingStart(Entity entity, CallbackInfo callback) {
        react$trackingEntity = entity;
    }

    @ModifyVariable(method = "addEntity", at = @At("STORE"), ordinal = 0, require = 0)
    private int react$trackingRange(int range) {
        Entity entity = react$trackingEntity;
        react$trackingEntity = null;
        return entity == null ? range : ModEntityRanges.trackingRange(entity, range);
    }
}
