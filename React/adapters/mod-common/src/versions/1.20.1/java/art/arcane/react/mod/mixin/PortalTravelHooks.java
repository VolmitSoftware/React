package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPortalHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Portal travel, where Bukkit fires its player and entity portal events once the destination is known. */
@Mixin(Entity.class)
public abstract class PortalTravelHooks {
    @Inject(method = "findDimensionEntryPoint", at = @At("RETURN"), cancellable = true, require = 0)
    private void react$travelling(ServerLevel level, CallbackInfoReturnable<PortalInfo> callback) {
        PortalInfo info = callback.getReturnValue();
        if (info != null && !ModPortalHost.allow((Entity) (Object) this, level, info.pos.x, info.pos.y, info.pos.z)) {
            callback.setReturnValue(null);
        }
    }
}
