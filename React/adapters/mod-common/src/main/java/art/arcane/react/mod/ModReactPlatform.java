package art.arcane.react.mod;

import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.telemetry.RuntimeTelemetry;
import art.arcane.react.platform.CapabilityRefusal;
import art.arcane.react.platform.CommandHost;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GrowthHost;
import art.arcane.react.platform.RedstoneHost;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.ExplosionHost;
import art.arcane.react.platform.FurnaceHost;
import art.arcane.react.platform.ComponentHost;
import art.arcane.react.platform.EntityEventHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.EntityActivityHost;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.MobStackHost;
import art.arcane.react.platform.ItemHost;
import art.arcane.react.platform.LanguageHost;
import art.arcane.react.platform.PlatformCapability;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.PlayerStatsHost;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.RegistryHost;
import art.arcane.react.platform.RuntimeStatsHost;
import art.arcane.react.platform.SamplerHost;
import art.arcane.react.platform.TickStatsHost;
import art.arcane.react.platform.VehicleHost;
import art.arcane.react.platform.ViewDistanceHost;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.platform.MapDataHost;
import art.arcane.react.platform.DropHost;
import art.arcane.react.platform.GameRuleHost;
import art.arcane.react.platform.IncidentHost;
import art.arcane.react.platform.PlayerHost;
import art.arcane.react.platform.ShorthandHost;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.platform.WorldStatsHost;
import art.arcane.react.platform.cost.OwnerCostProbe;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/** React's platform on a Fabric, Forge or NeoForge server. Region and entity scheduling collapse onto the server thread. */
public final class ModReactPlatform implements ReactPlatform {
    private final String loader;
    private final MinecraftServer server;
    private final File dataFolder;
    private final Logger logger;
    private final ModTicks ticks;
    private final ExecutorService async;
    private final ModPlatformScheduler scheduler;
    private final SamplerHost samplerHost;
    private final RegistryHost registryHost;
    private final TickStatsHost tickStatsHost;
    private final WorldStatsHost worldStatsHost;
    private final PlayerStatsHost playerStatsHost;
    private final RuntimeStatsHost runtimeStatsHost;
    private final CommandHost commandHost;
    private final LanguageHost languageHost;
    private final OwnerCostProbe costProbe;
    private final ModOwnerCost ownerCost;
    private final Supplier<ReactWebServer> webServer;
    private final ModComponentHost componentHost;
    private final ModEntityKiller entityKiller = new ModEntityKiller();
    private final ModEntityTicker entityTicker;
    private final WorldHost worldHost;
    private final EntityHost entityHost;
    private final HopperHost hopperHost;
    private final ModViewDistanceHost viewDistanceHost;
    private final ModChunkHost chunkHost;
    private final ModPlayerHost playerHost;
    private final ModIncidentHost incidentHost;
    private final ModGameRuleHost gameRuleHost;
    private final ModDropHost dropHost;
    private final ModShorthandHost shorthandHost;
    private final ModMapData mapData;
    // Batch A: items
    private final ItemHost itemHost = new ModItemHost();
    private final VehicleHost vehicleHost;
    private final EntityEventHost entityEventHost;
    // Batch A: ai
    private final EntityActivityHost entityActivityHost = new ModEntityActivityHost();
    // Batch A: mobs
    private final MobStackHost mobStackHost;

    private final BlockHost blockHost;
    private final GrowthHost growthHost;
    private final RedstoneHost redstoneHost = new ModRedstoneHost();
    private final SpawnHost spawnHost;
    private final ExplosionHost explosionHost = new ModExplosionHost(this::server);
    private final FurnaceHost furnaceHost;

    ModReactPlatform(String loader, MinecraftServer server, File dataFolder, Logger logger, ModTicks ticks, ExecutorService async,
                     List<Runnable> startupTasks, LanguageHost languageHost, OwnerCostProbe costProbe, Supplier<SampleController> samples,
                     Supplier<ReactWebServer> webServer, ModMaps maps, RuntimeTelemetry telemetry) {
        this.loader = loader;
        this.server = server;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.ticks = ticks;
        this.async = async;
        this.webServer = webServer;
        this.languageHost = languageHost;
        this.costProbe = costProbe;
        this.commandHost = new ModCommandHost(server, maps, new ModLanguageCommand(ticks::later));
        this.scheduler = new ModPlatformScheduler(server, ticks, async, telemetry.jobs());
        ModSamplerHost modSamplerHost = new ModSamplerHost(samples, maps);
        this.samplerHost = modSamplerHost;
        this.registryHost = new ModRegistryHost(dataFolder, startupTasks, this::verbose, this::warn);
        this.tickStatsHost = new ModTickStatsHost(server, ticks);
        this.worldStatsHost = new ModWorldStatsHost(server);
        this.playerStatsHost = new ModPlayerStatsHost(server);
        this.ownerCost = new ModOwnerCost(costProbe, samples);
        this.runtimeStatsHost = new ModRuntimeStatsHost(telemetry, modSamplerHost.chunks(), ownerCost);
        this.mapData = new ModMapData(server, modSamplerHost.chunks(), ownerCost, maps::frameAnchor);
        this.componentHost = new ModComponentHost(ticks, async, telemetry.jobs(), scheduler::isReady, logger, registryHost, mapData::isInstalled,
                () -> languageHost instanceof ModLanguageHost && ((ModLanguageHost) languageHost).integrationSecretsEnabled());
        this.worldHost = new ModWorldHost(server);
        this.entityTicker = new ModEntityTicker(server, logger);
        this.entityHost = new ModEntityHost(server, entityKiller, entityTicker);
        this.viewDistanceHost = new ModViewDistanceHost(server);
        // Batch A: items
        this.vehicleHost = new ModVehicleHost(server);
        this.entityEventHost = new ModEntityEventHost((ModWorldHost) worldHost, entityKiller);
        // Batch A: mobs
        this.mobStackHost = new ModMobStackHost(server);
        this.chunkHost = new ModChunkHost(server, modSamplerHost.chunks());
        this.playerHost = new ModPlayerHost(server);
        this.incidentHost = new ModIncidentHost();
        this.gameRuleHost = new ModGameRuleHost();
        this.dropHost = new ModDropHost();
        this.shorthandHost = new ModShorthandHost(server);
        this.furnaceHost = new ModFurnaceHost(server);
        this.spawnHost = new ModSpawnHost(server);
        this.blockHost = new ModBlockHost(server);
        this.growthHost = new ModGrowthHost(server);
        this.hopperHost = new ModHopperHost(server, modSamplerHost.chunks());
    }

    ModComponentHost components() {
        return componentHost;
    }

    ModEntityKiller entityKiller() {
        return entityKiller;
    }

    ModEntityTicker entityTicker() {
        return entityTicker;
    }

    Optional<ModIntegrationMetrics> integrations() {
        return Optional.of(mapData.integrations());
    }

    ModViewDistanceHost viewDistances() {
        return viewDistanceHost;
    }

    ModChunkSamples chunkSamples() {
        return ((ModSamplerHost) samplerHost).chunks();
    }

    ModOwnerCost ownerCost() {
        return ownerCost;
    }

    ModMapData mapData() {
        return mapData;
    }

    ModPlatformScheduler modScheduler() {
        return scheduler;
    }

    MinecraftServer server() {
        return server;
    }

    @Override
    public PlatformKind kind() {
        return PlatformKind.MODDED;
    }

    @Override
    public String platformName() {
        return loader;
    }

    @Override
    public String minecraftVersion() {
        return VanillaApi.minecraftVersion();
    }

    @Override
    public File dataFolder() {
        return dataFolder;
    }

    @Override
    public PlatformScheduler scheduler() {
        return scheduler;
    }

    @Override
    public ExecutorService asyncExecutor() {
        return async;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void warn(String message, Throwable failure) {
        logger.warn(message, failure);
    }

    @Override
    public void verbose(String message) {
        logger.debug(message);
    }

    @Override
    public void reportError(String context, Throwable failure) {
        logger.error(context, failure);
    }

    @Override
    public Optional<CapabilityRefusal> unavailable(PlatformCapability capability) {
        return Optional.empty();
    }

    @Override
    public boolean dispatchConsoleCommand(String command) {
        String line = command.startsWith("/") ? command.substring(1) : command;
        Runnable dispatch = () -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), line);
        if (server.isSameThread()) {
            dispatch.run();
            return true;
        }
        return ticks.later(dispatch);
    }

    @Override
    public Optional<ReactPlayer> player(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player == null ? Optional.empty() : Optional.of(new ModReactPlayer(player));
    }

    @Override
    public Optional<ReactPlayer> player(Object handle) {
        if (handle instanceof ReactPlayer player) {
            return Optional.of(player);
        }
        return handle instanceof ServerPlayer player ? Optional.of(new ModReactPlayer(player)) : Optional.empty();
    }

    @Override
    public Optional<ReactWorld> world(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (ServerLevel level : server.getAllLevels()) {
            String id = ModReactWorld.nameOf(level);
            if (id.equals(name) || VanillaApi.keyPath(level.dimension()).equals(name)) {
                return Optional.of(new ModReactWorld(level));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ReactWorld> world(Object handle) {
        if (handle instanceof ReactWorld world) {
            return Optional.of(world);
        }
        return handle instanceof ServerLevel level ? Optional.of(new ModReactWorld(level)) : Optional.empty();
    }

    // Commands, messaging and localization

    @Override
    public Optional<ReactCommandSender> commandSender(Object handle) {
        if (handle instanceof ReactCommandSender sender) {
            return Optional.of(sender);
        }
        if (handle instanceof CommandSourceStack source) {
            return Optional.of(new ModCommandSender(source));
        }
        return handle instanceof ServerPlayer player ? Optional.of(new ModCommandSender(player.createCommandSourceStack()))
                : Optional.empty();
    }

    @Override
    public Optional<CommandHost> commandHost() {
        return Optional.of(commandHost);
    }

    @Override
    public Optional<LanguageHost> languageHost() {
        return Optional.of(languageHost);
    }

    // Features, tweaks and actions
    @Override
    public Optional<ComponentHost> componentHost() {
        return Optional.of(componentHost);
    }

    @Override
    public Optional<WorldHost> worldHost() {
        return Optional.of(worldHost);
    }

    @Override
    public Optional<EntityHost> entityHost() {
        return Optional.of(entityHost);
    }

    @Override
    public Optional<HopperHost> hopperHost() {
        return Optional.of(hopperHost);
    }

    @Override
    public Optional<ViewDistanceHost> viewDistanceHost() {
        return Optional.of(viewDistanceHost);
    }

    // Batch A: items
    @Override
    public Optional<ItemHost> itemHost() {
        return Optional.of(itemHost);
    }

    @Override
    public Optional<VehicleHost> vehicleHost() {
        return Optional.of(vehicleHost);
    }

    @Override
    public Optional<EntityEventHost> entityEventHost() {
        return Optional.of(entityEventHost);
    }

    // Batch A: ai
    @Override
    public Optional<EntityActivityHost> entityActivityHost() {
        return Optional.of(entityActivityHost);
    }

    // Batch A: mobs
    @Override
    public Optional<MobStackHost> mobStackHost() {
        return Optional.of(mobStackHost);
    }

    // Chunks, players, world rules and maps

    @Override
    public Optional<ChunkHost> chunkHost() {
        return Optional.of(chunkHost);
    }

    @Override
    public Optional<PlayerHost> playerHost() {
        return Optional.of(playerHost);
    }

    @Override
    public Optional<IncidentHost> incidentHost() {
        return Optional.of(incidentHost);
    }

    @Override
    public Optional<GameRuleHost> gameRuleHost() {
        return Optional.of(gameRuleHost);
    }

    @Override
    public Optional<DropHost> dropHost() {
        return Optional.of(dropHost);
    }

    @Override
    public Optional<ShorthandHost> shorthandHost() {
        return Optional.of(shorthandHost);
    }

    @Override
    public Optional<IntegrationMetricsHost> integrationMetricsHost() {
        return Optional.of(mapData.integrations());
    }

    @Override
    public Optional<ChunkHeatmapHost> chunkHeatmapHost() {
        return Optional.of(mapData.heatmaps());
    }

    @Override
    public Optional<MapAnchorHost> mapAnchorHost() {
        return Optional.of(mapData.anchors());
    }

    @Override
    public Optional<MapDataHost> mapDataHost() {
        return Optional.of(mapData);
    }

    // Owner cost

    @Override
    public Optional<OwnerCostProbe> ownerCostProbe() {
        return Optional.of(costProbe);
    }

    // React Web

    @Override
    public Optional<ReactWebServer> webServer() {
        return Optional.ofNullable(webServer.get());
    }

    // Sampler hosts

    @Override
    public Optional<SamplerHost> samplerHost() {
        return Optional.of(samplerHost);
    }

    @Override
    public Optional<RegistryHost> registryHost() {
        return Optional.of(registryHost);
    }

    @Override
    public Optional<TickStatsHost> tickStatsHost() {
        return Optional.of(tickStatsHost);
    }

    @Override
    public Optional<WorldStatsHost> worldStatsHost() {
        return Optional.of(worldStatsHost);
    }

    @Override
    public Optional<PlayerStatsHost> playerStatsHost() {
        return Optional.of(playerStatsHost);
    }

    @Override
    public Optional<RuntimeStatsHost> runtimeStatsHost() {
        return Optional.of(runtimeStatsHost);
    }

    // Block, redstone, spawn and explosion hosts

    @Override
    public Optional<BlockHost> blockHost() {
        return Optional.of(blockHost);
    }

    @Override
    public Optional<GrowthHost> growthHost() {
        return Optional.of(growthHost);
    }

    @Override
    public Optional<RedstoneHost> redstoneHost() {
        return Optional.of(redstoneHost);
    }

    @Override
    public Optional<SpawnHost> spawnHost() {
        return Optional.of(spawnHost);
    }


    @Override
    public Optional<ExplosionHost> explosionHost() {
        return Optional.of(explosionHost);
    }

    @Override
    public Optional<FurnaceHost> furnaceHost() {
        return Optional.of(furnaceHost);
    }
}
