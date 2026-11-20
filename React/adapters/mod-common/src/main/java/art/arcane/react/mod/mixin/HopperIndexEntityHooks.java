package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModHopperHost;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dropped items entering or leaving a level, for the hopper item index. */
@Pseudo
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class HopperIndexEntityHooks {
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 0)
    private void react$itemTracked(Entity entity, CallbackInfo callback) {
        ModHopperHost.itemTracked(entity);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 0)
    private void react$itemUntracked(Entity entity, CallbackInfo callback) {
        ModHopperHost.itemUntracked(entity);
    }
}
