package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Player teleports where Bukkit fires PlayerTeleportEvent: commands, ender pearls, mods and dimension changes go through
 * a teleport transition, chorus fruit and dismounting through a plain position teleport. Portal trips fire the portal
 * event instead. A cancelled teleport leaves the player put.
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerTeleportHooks {
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void react$transition(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callback) {
        if (ModPlayerHost.isPortalTransition(transition) || transition.newLevel() == null) {
            return;
        }
        if (ModPlayerHost.cancelsTeleport((ServerPlayer) (Object) this, transition.newLevel(), transition.relatives(),
                transition.position().x, transition.position().y, transition.position().z)) {
            callback.setReturnValue(null);
        }
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$position(double x, double y, double z, CallbackInfo callback) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (ModPlayerHost.cancelsTeleport(player, player.level(), Set.of(), x, y, z)) {
            callback.cancel();
        }
    }
}
