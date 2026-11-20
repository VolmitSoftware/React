package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import art.arcane.react.mod.PortalTrip;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Portal trips, where Bukkit fires PlayerPortalEvent and EntityPortalEvent. On 1.20.1 every dimension change asks the
 * entity for its entry point, and a cancelled trip gets none, so the entity stays on this side of the portal.
 */
@Mixin(Entity.class)
public abstract class PortalHooks {
    @Inject(method = "findDimensionEntryPoint", at = @At("RETURN"), cancellable = true, require = 0)
    private void react$portal(ServerLevel level, CallbackInfoReturnable<PortalInfo> callback) {
        Entity entity = (Entity) (Object) this;
        PortalTrip resumed = PortalTrip.resuming(entity);
        if (resumed != null) {
            callback.setReturnValue(resumed.info());
            return;
        }
        PortalInfo info = callback.getReturnValue();
        if (info != null && ModPlayerHost.cancelsPortal(entity, new PortalTrip(entity, level, info))) {
            callback.setReturnValue(null);
        }
    }
}
