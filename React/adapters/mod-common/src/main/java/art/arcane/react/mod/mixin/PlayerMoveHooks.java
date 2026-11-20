package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player movement where Bukkit fires PlayerMoveEvent: a movement packet, handled on the server thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerMoveHooks {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$move(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        if (ModPlayerHost.cancelsMove((ServerGamePacketListenerImpl) (Object) this, player, packet)) {
            callback.cancel();
        }
    }
}
