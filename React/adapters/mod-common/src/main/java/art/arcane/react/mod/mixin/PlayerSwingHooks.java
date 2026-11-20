package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An arm swing, where Bukkit fires PlayerInteractEvent with LEFT_CLICK_AIR when the swing hits no block. It runs once
 * the packet reached the server thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerSwingHooks {
    @Shadow
    public ServerPlayer player;

    @Inject(method = {"handlePunch", "handleAnimate"}, at = @At("TAIL"), require = 0)
    private void react$swing(CallbackInfo callback) {
        ModPlayerHost.swung(player);
    }
}
