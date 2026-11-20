package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Command, mod, ender pearl and chorus fruit teleports of a player, where Bukkit fires PlayerTeleportEvent. A cancelled one leaves the player put.
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerTeleportHooks {
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void react$teleport(ServerLevel level, double x, double y, double z, Set<?> relatives, float yaw, float pitch,
                                CallbackInfoReturnable<Boolean> callback) {
        if (ModPlayerHost.cancelsTeleport((ServerPlayer) (Object) this, level, relatives, x, y, z)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$position(double x, double y, double z, CallbackInfo callback) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (ModPlayerHost.cancelsTeleport(player, player.serverLevel(), Set.of(), x, y, z)) {
            callback.cancel();
        }
    }
}
