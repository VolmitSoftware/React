package art.arcane.react.neoforge;

import art.arcane.react.mod.ReactMapsPayload;
import art.arcane.react.mod.ReactModRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Clients accept React's map channel, so a client with React installed passes NeoForge's payload handler check. */
@Mod(value = ReactModRuntime.ID, dist = Dist.CLIENT)
public final class ReactNeoForgeClient {
    public ReactNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> event.register(ReactMapsPayload.TYPE, (payload, context) -> { }));
    }
}
