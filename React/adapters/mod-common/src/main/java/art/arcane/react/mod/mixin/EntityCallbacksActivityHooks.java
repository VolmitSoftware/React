package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityActivityHost;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EntityCallbacksActivityHooks {
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 0)
    private void react$entityLoaded(Entity entity, CallbackInfo callback) {
        ModEntityActivityHost.trackingStarted(entity);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), require = 0)
    private void react$entityUnloading(Entity entity, CallbackInfo callback) {
        ModEntityActivityHost.trackingEnded(entity);
    }
}
