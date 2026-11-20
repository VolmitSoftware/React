package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModDropHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class DropCaptureHooks {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$captureDrop(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (ModDropHost.captureItem((ServerLevel) (Object) this, entity)) {
            callback.setReturnValue(true);
        }
    }
}
