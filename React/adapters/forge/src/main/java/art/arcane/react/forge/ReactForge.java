package art.arcane.react.forge;

import art.arcane.react.forge.cost.ForgeEventAttribution;
import art.arcane.react.forge.cost.ForgeModOwners;
import art.arcane.react.mod.ReactMapsPayload;
import art.arcane.react.mod.ReactModRuntime;
import art.arcane.react.mod.cost.EventAttribution;
import art.arcane.react.mod.cost.ModCostProbe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ChannelBuilder;

@Mod(ReactModRuntime.ID)
public final class ReactForge {
    private final ReactModRuntime runtime = new ReactModRuntime("Forge", FMLPaths.CONFIGDIR.get(), costProbe());

    public ReactForge(FMLJavaModLoadingContext context) {
        ChannelBuilder.named(ReactMapsPayload.TYPE.id()).optional().payloadChannel().play()
                .bidirectional().add(ReactMapsPayload.TYPE, ReactMapsPayload.CODEC.cast(), ReactForge::mapsPayload)
                .build();
        RegisterCommandsEvent.BUS.addListener(event -> runtime.registerCommands(event.getDispatcher()));
        ServerStartedEvent.BUS.addListener(event -> runtime.start(event.getServer()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> runtime.tick());
        ServerStoppingEvent.BUS.addListener(event -> runtime.stop());
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ReactForgeClient.initialize();
        }
    }

    private static ModCostProbe costProbe() {
        EventAttribution events = null;
        String reason = null;
        try {
            events = new ForgeEventAttribution();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            reason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        return new ModCostProbe(new ForgeModOwners(), events, reason);
    }

    private static void mapsPayload(ReactMapsPayload payload, CustomPayloadEvent.Context context) {
        context.setPacketHandled(true);
    }
}
