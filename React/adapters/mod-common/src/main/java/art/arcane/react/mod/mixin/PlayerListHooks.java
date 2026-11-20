package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Players joining and leaving, where Bukkit fires PlayerJoinEvent and PlayerQuitEvent.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListHooks {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"), require = 0)
    private void react$joined(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo callback) {
        ModPlayerHost.joined(player);
    }

    @Inject(method = "remove", at = @At("HEAD"), require = 0)
    private void react$left(ServerPlayer player, CallbackInfo callback) {
        ModPlayerHost.left(player);
    }
}
