package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import art.arcane.react.mod.SendDistanceAware;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Caps the chunk radius sent to one player, as Paper's per-player send view distance does.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapViewHooks implements SendDistanceAware {
    @Inject(method = "getPlayerViewDistance", at = @At("RETURN"), cancellable = true, require = 0)
    private void react$capSendDistance(ServerPlayer player, CallbackInfoReturnable<Integer> callback) {
        int distance = callback.getReturnValue();
        ModPlayerHost.rememberNaturalDistance(player, distance);
        int capped = ModPlayerHost.capViewDistance(player, distance);
        if (capped != distance) {
            callback.setReturnValue(capped);
        }
    }
}
