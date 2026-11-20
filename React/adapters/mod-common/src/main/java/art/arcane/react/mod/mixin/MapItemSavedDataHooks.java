package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModMapHooks;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataHooks {
    @Inject(method = "getUpdatePacket(Lnet/minecraft/world/level/saveddata/maps/MapId;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/network/protocol/Packet;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void react$ownDelivery(MapId id, Player player, CallbackInfoReturnable<Packet<?>> callback) {
        if (ModMapHooks.isClaimed(id.id())) {
            callback.setReturnValue(ModMapHooks.carriedUpdate((MapItemSavedData) (Object) this, id.id(), player));
        }
    }
}
