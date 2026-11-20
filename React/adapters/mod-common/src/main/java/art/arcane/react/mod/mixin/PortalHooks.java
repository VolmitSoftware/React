package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Portal trips, where Bukkit fires PlayerPortalEvent and EntityPortalEvent. A cancelled trip gets no destination, so
 * the entity stays on this side of the portal.
 */
@Mixin(PortalProcessor.class)
public abstract class PortalHooks {
    @Inject(method = "getPortalDestination", at = @At("RETURN"), cancellable = true, require = 0)
    private void react$portal(ServerLevel level, Entity entity, CallbackInfoReturnable<?> callback) {
        if (ModPlayerHost.cancelsPortal(entity, callback.getReturnValue())) {
            callback.setReturnValue(null);
        } else {
            ModPlayerHost.markPortalTransition(callback.getReturnValue());
        }
    }
}
