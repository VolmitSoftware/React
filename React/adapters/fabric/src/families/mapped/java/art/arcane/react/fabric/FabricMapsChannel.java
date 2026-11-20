package art.arcane.react.fabric;

import art.arcane.react.mod.ReactMapsPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

final class FabricMapsChannel {
    private FabricMapsChannel() {
    }

    static void register() {
        PayloadTypeRegistry.playS2C().register(ReactMapsPayload.TYPE, ReactMapsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ReactMapsPayload.TYPE, ReactMapsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReactMapsPayload.TYPE, (ReactMapsPayload payload, ServerPlayNetworking.Context context) -> { });
    }
}
