package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Command and mod teleports of a player, where Bukkit fires PlayerTeleportEvent. A cancelled one leaves the player put.
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerTeleportHooks {
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void react$teleport(ServerLevel level, double x, double y, double z, Set<?> relatives, float yaw, float pitch,
                                boolean resetCamera, CallbackInfoReturnable<Boolean> callback) {
        if (ModPlayerHost.cancelsTeleport((ServerPlayer) (Object) this, level, relatives, x, y, z)) {
            callback.setReturnValue(false);
        }
    }
}
