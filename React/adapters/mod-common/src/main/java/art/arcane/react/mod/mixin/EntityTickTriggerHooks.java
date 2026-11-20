package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityTicker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bukkit's EntityPoseChangeEvent and EntityDropItemEvent, as entity tick triggers. */
@Mixin(Entity.class)
public abstract class EntityTickTriggerHooks {
    @Inject(method = "setPose", at = @At("HEAD"), require = 0)
    private void react$posing(Pose pose, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (entity.getPose() != pose) {
            ModEntityTicker.poke(entity);
        }
    }

    @Inject(method = "spawnAtLocation", at = @At("RETURN"), require = 0)
    private void react$dropped(CallbackInfoReturnable<ItemEntity> callback) {
        if (callback.getReturnValue() != null) {
            ModEntityTicker.poke((Entity) (Object) this);
        }
    }
}
