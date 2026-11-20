package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ClientLanguage;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla 1.20.1 reads a player's client language from the options packet without keeping it. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerHooks implements ClientLanguage {
    @Unique
    private volatile String react$language = DEFAULT;

    @Inject(method = "updateOptions", at = @At("HEAD"), require = 0)
    private void react$options(ServerboundClientInformationPacket packet, CallbackInfo callback) {
        react$language = packet.language();
    }

    @Override
    public String react$language() {
        return react$language;
    }
}
