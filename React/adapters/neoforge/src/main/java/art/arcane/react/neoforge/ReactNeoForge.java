package art.arcane.react.neoforge;

import art.arcane.react.mod.ReactMapsPayload;
import art.arcane.react.mod.ReactModRuntime;
import art.arcane.react.mod.cost.EventAttribution;
import art.arcane.react.mod.cost.ModCostProbe;
import art.arcane.react.neoforge.cost.NeoForgeEventAttribution;
import art.arcane.react.neoforge.cost.NeoForgeModOwners;
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
    private final ReactModRuntime runtime = new ReactModRuntime("NeoForge", FMLPaths.CONFIGDIR.get(), costProbe());

    public ReactNeoForge(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> runtime.registerCommands(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> runtime.start(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> runtime.tick());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> runtime.stop());
    }

    private static ModCostProbe costProbe() {
        EventAttribution events = null;
        String reason = null;
        try {
            events = new NeoForgeEventAttribution(NeoForge.EVENT_BUS);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            reason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        return new ModCostProbe(new NeoForgeModOwners(), events, reason);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional()
                .playBidirectional(ReactMapsPayload.TYPE, ReactMapsPayload.CODEC, (payload, context) -> { });
    }
}
