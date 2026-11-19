package art.arcane.react.neoforge;

import art.arcane.react.mod.ReactMapsPayload;
import art.arcane.react.mod.ReactModRuntime;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(ReactModRuntime.ID)
public final class ReactNeoForge {
    private final ReactModRuntime runtime = new ReactModRuntime("NeoForge", FMLPaths.CONFIGDIR.get());

    public ReactNeoForge(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> runtime.registerCommands(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> runtime.start(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> runtime.tick());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> runtime.stop());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional()
                .playBidirectional(ReactMapsPayload.TYPE, ReactMapsPayload.CODEC, (payload, context) -> { });
    }
}
