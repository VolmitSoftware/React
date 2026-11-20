package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPortalHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Portal travel, where Bukkit fires its player and entity portal events once the destination is known. */
@Mixin(Entity.class)
public abstract class PortalTravelHooks {
    @Inject(method = "teleportToPortalDestination", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$travelling(ServerLevel level, TeleportTransition transition, CallbackInfo callback) {
        if (!ModPortalHost.allow((Entity) (Object) this, transition.newLevel(), transition.position().x, transition.position().y,
                transition.position().z)) {
            callback.cancel();
        }
    }
}
