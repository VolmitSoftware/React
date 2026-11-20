package art.arcane.react.fabric;

import art.arcane.react.mod.ReactMapsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

final class FabricMapsChannel {
    private FabricMapsChannel() {
    }

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ReactMapsPayload.ID, (server, player, handler, buffer, sender) -> { });
    }
}
