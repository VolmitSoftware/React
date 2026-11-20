package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityRanges;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelActivationHooks {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$activation(Entity entity, CallbackInfo callback) {
        if (ModEntityRanges.tickInactive((ServerLevel) (Object) this, entity)) {
            callback.cancel();
        }
    }
}
