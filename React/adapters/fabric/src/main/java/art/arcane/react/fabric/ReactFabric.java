package art.arcane.react.fabric;

import art.arcane.react.mod.ReactMapsPayload;
import art.arcane.react.mod.ReactModRuntime;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

public final class ReactFabric implements ModInitializer {
    private final ReactModRuntime runtime = new ReactModRuntime("Fabric", FabricLoader.getInstance().getConfigDir());

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(ReactMapsPayload.TYPE, ReactMapsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ReactMapsPayload.TYPE, ReactMapsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReactMapsPayload.TYPE, (payload, context) -> { });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> runtime.registerCommands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(runtime::start);
        ServerTickEvents.END_SERVER_TICK.register(server -> runtime.tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> runtime.stop());
    }
}
