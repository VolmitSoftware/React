package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModSettings;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands chat to an open settings prompt before vanilla handles it, as React's chat listener does on Bukkit. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatInputHooks {
    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$chat(ServerboundChatPacket packet, CallbackInfo callback) {
        if (ModSettings.onChat(((ServerGamePacketListenerImpl) (Object) this).player, packet.message())) {
            callback.cancel();
        }
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"), require = 0)
    private void react$disconnect(CallbackInfo callback) {
        ModSettings.forget(((ServerGamePacketListenerImpl) (Object) this).player);
    }
}
