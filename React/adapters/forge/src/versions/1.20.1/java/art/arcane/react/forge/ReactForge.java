package art.arcane.react.forge;

import art.arcane.react.forge.cost.ForgeEventAttribution;
import art.arcane.react.forge.cost.ForgeModOwners;
import art.arcane.react.mod.ReactMapsPayload;
import art.arcane.react.mod.ReactModRuntime;
import art.arcane.react.mod.cost.EventAttribution;
import art.arcane.react.mod.cost.ModCostProbe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkRegistry;

@Mod(ReactModRuntime.ID)
public final class ReactForge {
    private static final String CHANNEL_VERSION = "1";

    private final ReactModRuntime runtime = new ReactModRuntime("Forge", FMLPaths.CONFIGDIR.get(), costProbe());

    public ReactForge() {
        // Optional on both sides, so vanilla clients and servers without React still connect.
        NetworkRegistry.ChannelBuilder.named(ReactMapsPayload.ID)
                .networkProtocolVersion(() -> CHANNEL_VERSION)
                .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(CHANNEL_VERSION))
                .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(CHANNEL_VERSION))
                .eventNetworkChannel();
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> runtime.registerCommands(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> runtime.start(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                runtime.tick();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> runtime.stop());
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ReactForgeClient.initialize();
        }
    }

    private static ModCostProbe costProbe() {
        EventAttribution events = null;
        String reason = null;
        try {
            events = new ForgeEventAttribution(MinecraftForge.EVENT_BUS);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            reason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        return new ModCostProbe(new ForgeModOwners(), events, reason);
    }
}
