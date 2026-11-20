package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModMobStackHost;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entities entering and leaving a level, standing in for Bukkit's EntitiesLoadEvent and EntityRemoveEvent. */
@Pseudo
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EntityCallbacksMobStackHooks {
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 0)
    private void react$loaded(Entity entity, CallbackInfo callback) {
        ModMobStackHost.loaded(entity);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 0)
    private void react$removed(Entity entity, CallbackInfo callback) {
        ModMobStackHost.removed(entity, false);
    }
}
