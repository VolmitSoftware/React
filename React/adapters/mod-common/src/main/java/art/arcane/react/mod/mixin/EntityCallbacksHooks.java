package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModMapHooks;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EntityCallbacksHooks {
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 0)
    private void react$trackingStarted(Entity entity, CallbackInfo callback) {
        ModMapHooks.trackingStarted(entity);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 0)
    private void react$trackingEnded(Entity entity, CallbackInfo callback) {
        ModMapHooks.trackingEnded(entity);
    }
}
