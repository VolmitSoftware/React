package art.arcane.react.mod;

import art.arcane.react.api.benchmark.BenchmarkRunner;
import art.arcane.react.api.benchmark.BenchmarkTarget;
import art.arcane.react.content.directorcommand.BridgeCommands;
import art.arcane.react.content.directorcommand.ChunkCommands;
import art.arcane.react.content.directorcommand.DistanceCommands;
import art.arcane.react.content.directorcommand.EnvironmentCommands;
import art.arcane.react.content.directorcommand.IntegrationCommands;
import art.arcane.react.core.bridge.BridgeHealthReport;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.EnvironmentMessages;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.platform.CommandHost;
import art.arcane.react.platform.CommandTargets;
import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.web.MclogsClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * The React subcommands a mod server can run. Leaves backed by Bukkit-only systems keep the unavailable reply:
 * monitor, config monitor, monitoring-only, the world actions, debug dump and entity-data, dev, test and plugin-api.
 */
final class ModCommandHost implements CommandHost {
    private final MinecraftServer server;
    private final ModMaps maps;
    private final ModLanguageCommand language;

    ModCommandHost(MinecraftServer server, ModMaps maps, ModLanguageCommand language) {
        this.server = server;
        this.maps = maps;
        this.language = language;
    }

    @Override
    public boolean language(ReactCommandSender sender, String[] arguments) {
        return language.execute(sender, arguments);
    }

    @Override
    public Optional<List<String>> completeLanguage(ReactCommandSender sender, String[] arguments) {
        return Optional.of(language.complete(sender, arguments));
    }

    @Override
    public List<String> rendererIds() {
        return maps.rendererIds();
    }

    @Override
    public void map(VolmitSender sender, String rendererId) {
        ReactPlayer player = sender.player();
        if (player != null && player.handle() instanceof ServerPlayer serverPlayer) {
            maps.map(serverPlayer, rendererId);
        }
    }

    @Override
    public void configGui(VolmitSender sender) {
        ReactPlayer player = sender.player();
        if (player != null && player.handle() instanceof ServerPlayer serverPlayer) {
            Optional<ModSettings> settings = ModSettings.current();
            if (settings.isEmpty()) {
                return;
            }
            if (!settings.get().canConfigure(serverPlayer.getUUID())) {
                ReactLanguage.sendPrefixed(sender, CommandMessages.CONFIG_PERMISSION);
                return;
            }
            settings.get().open(serverPlayer);
        }
    }

    @Override
    public List<CommandTargets.Player> players() {
        List<CommandTargets.Player> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            players.add(new CommandTargets.Player(new ModReactPlayer(player)));
        }
        return players;
    }

    @Override
    public List<CommandTargets.World> worlds() {
        List<CommandTargets.World> worlds = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            worlds.add(target(level));
        }
        return worlds;
    }

    @Override
    public Optional<CommandTargets.World> worldOf(ReactPlayer player) {
        return player.handle() instanceof ServerPlayer serverPlayer ? Optional.of(target(VanillaApi.level(serverPlayer))) : Optional.empty();
    }

    private static CommandTargets.World target(ServerLevel level) {
        return new CommandTargets.World(new ModReactWorld(level), ModReactWorld.nameOf(level));
    }

    @Override
    public void benchmark(VolmitSender sender, Set<BenchmarkTarget> targets) {
        BenchmarkRunner.run(sender, targets);
    }

    @Override
    public void bridgeStatus(VolmitSender sender) {
        BridgeCommands.status(sender, new BridgeHealthReport(List.of()));
    }

    @Override
    public void chunkSample(VolmitSender sender) {
        ServerPlayer player = serverPlayer(sender);
        if (player == null) {
            return;
        }
        ServerLevel level = VanillaApi.level(player);
        Optional<SampledChunk> chunk = platform().chunkSamples().chunk(ModReactWorld.nameOf(level),
                VanillaApi.chunkX(player.chunkPosition()), VanillaApi.chunkZ(player.chunkPosition()));
        if (chunk.isEmpty()) {
            ReactLanguage.sendPrefixed(sender, CommandMessages.CHUNK_NOT_SAMPLED);
            return;
        }
        ChunkCommands.sendSamples(sender, chunk.get());
    }

    @Override
    public void chunkWorst(VolmitSender sender) {
        ServerPlayer player = serverPlayer(sender);
        Optional<SampledChunk> worst = platform().chunkSamples().worst();
        ServerLevel level = worst.isEmpty() ? null : level(worst.get().getWorldKey());
        if (player == null || level == null) {
            ReactLanguage.sendPrefixed(sender, CommandMessages.NO_CHUNKS_SAMPLED);
            return;
        }
        SampledChunk chunk = worst.get();
        double x = (chunk.getChunkX() << 4) + 8.5D;
        double z = (chunk.getChunkZ() << 4) + 8.5D;
        String teleport = String.format(Locale.ROOT, "execute in %s positioned %.1f 0 %.1f positioned over motion_blocking run tp %s ~ ~ ~",
                VanillaApi.dimensionId(level.dimension()), x, z, player.getUUID().toString());
        if (!ReactPlatforms.get().dispatchConsoleCommand(teleport)) {
            ReactPlatforms.get().reportError("Failed to schedule worst sampled chunk teleport", new IllegalStateException(teleport));
        }
        ChunkCommands.sendSamples(sender, chunk);
    }

    @Override
    public void serverDistance(VolmitSender sender, DistanceKind kind, int distance) {
        DistanceCommands.Reply reply = reply(sender);
        if (!DistanceCommands.isValidWorldDistance(kind, distance)) {
            DistanceCommands.invalid(reply, false);
            return;
        }
        if (kind == DistanceKind.SEND) {
            DistanceCommands.unavailable(reply, "server", kind);
            return;
        }
        onServerThread(reply, () -> {
            try {
                if (kind == DistanceKind.VIEW) {
                    platform().viewDistances().setServerViewDistance(distance);
                } else {
                    platform().viewDistances().setServerSimulationDistance(distance);
                }
                DistanceCommands.set(reply, "server", DistanceCommands.SERVER_TARGET, kind, distance);
            } catch (RuntimeException | LinkageError failure) {
                distanceFailed(reply, "server", DistanceCommands.SERVER_TARGET, kind, failure);
            }
        });
    }

    @Override
    public void worldDistance(VolmitSender sender, CommandTargets.World world, DistanceKind kind, int distance) {
        DistanceCommands.Reply reply = reply(sender);
        if (!DistanceCommands.isValidWorldDistance(kind, distance)) {
            DistanceCommands.invalid(reply, false);
            return;
        }
        if (kind == DistanceKind.SEND) {
            DistanceCommands.unavailable(reply, "world", kind);
            return;
        }
        onServerThread(reply, () -> {
            try {
                if (kind == DistanceKind.VIEW) {
                    platform().viewDistances().setViewDistance(world.world(), distance);
                } else {
                    platform().viewDistances().setSimulationDistance(world.world(), distance);
                }
                DistanceCommands.set(reply, "world", world.key(), kind, distance);
            } catch (RuntimeException | LinkageError failure) {
                distanceFailed(reply, "world", world.key(), kind, failure);
            }
        });
    }

    /** Vanilla keeps one distance per level, so players cannot get their own. */
    @Override
    public void playerDistance(VolmitSender sender, CommandTargets.Player player, DistanceKind kind, int distance) {
        DistanceCommands.Reply reply = reply(sender);
        if (!DistanceCommands.isValidPlayerDistance(distance)) {
            DistanceCommands.invalid(reply, true);
            return;
        }
        DistanceCommands.unavailable(reply, "player", kind);
    }

    @Override
    public void environmentInfo(VolmitSender sender) {
        String version = reactVersion();
        String serverVersion = ReactPlatforms.get().platformName() + " " + server.getServerVersion();
        EnvironmentCommands.info(sender, version, serverVersion);
        String summary = EnvironmentCommands.summary(version, serverVersion);
        ReactPlatforms.get().asyncExecutor().execute(() -> uploadEnvironment(sender, summary, version));
    }

    @Override
    public void integrationStatus(VolmitSender sender) {
        Optional<ModIntegrationMetrics> metrics = platform().integrations();
        if (metrics.isEmpty()) {
            ReactLanguage.sendPrefixed(sender, CommandMessages.INTEGRATION_NOT_READY);
            return;
        }
        List<IntegrationCommands.Entry> entries = new ArrayList<>();
        for (String id : metrics.get().ids()) {
            IntegrationMetricsHost.Status status = metrics.get().status(id);
            entries.add(new IntegrationCommands.Entry(id,
                    status == null ? "unknown" : status.health().toLowerCase(Locale.ROOT), "-",
                    status == null ? 0L : metrics.get().refreshedAtMs(),
                    status == null ? "" : status.message()));
        }
        IntegrationCommands.status(sender, entries, null, List.of(), System.currentTimeMillis());
    }

    private void uploadEnvironment(VolmitSender sender, String summary, String version) {
        Runnable reply;
        try {
            URI url = new MclogsClient().publish(summary, "VolmitSoftware - React - v" + version, "VolmitSoftware/React/" + version);
            Component link = ReactLanguage.component(EnvironmentMessages.UPLOAD_LINK).clickEvent(ClickEvent.openUrl(url.toString()));
            reply = () -> sender.getS().send(ComponentText.component(link));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            ReactPlatforms.get().warn("React environment report upload was interrupted", interrupted);
            reply = () -> ReactLanguage.send(sender, EnvironmentMessages.UPLOAD_FAILED);
        } catch (IOException | RuntimeException failure) {
            ReactPlatforms.get().warn("Unable to upload React environment report to mclo.gs", failure);
            reply = () -> ReactLanguage.send(sender, EnvironmentMessages.UPLOAD_FAILED);
        }
        Runnable delivery = reply;
        ReactPlatforms.get().scheduler().global(() -> LanguageAudience.run(sender.getS().playerId().orElse(null), delivery), 0);
    }

    private static String reactVersion() {
        try (InputStream input = ModCommandHost.class.getResourceAsStream("react-mod.properties")) {
            if (input == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty("version", "unknown");
        } catch (IOException unreadable) {
            return "unknown";
        }
    }

    private void onServerThread(DistanceCommands.Reply reply, Runnable operation) {
        if (server.isSameThread()) {
            operation.run();
        } else if (!ReactPlatforms.get().scheduler().global(operation, 0)) {
            DistanceCommands.scheduleFailed(reply);
        }
    }

    private static void distanceFailed(DistanceCommands.Reply reply, String scope, String target, DistanceKind kind, Throwable failure) {
        ReactPlatforms.get().reportError(DistanceCommands.failureContext(scope, target, kind), failure);
        DistanceCommands.failed(reply, scope, target, kind);
    }

    private static DistanceCommands.Reply reply(VolmitSender sender) {
        return (MessageKey key, MessageArgument... arguments) -> ReactLanguage.sendPrefixed(sender, key, arguments);
    }

    private ServerLevel level(String worldKey) {
        for (ServerLevel level : server.getAllLevels()) {
            if (ModReactWorld.nameOf(level).equals(worldKey)) {
                return level;
            }
        }
        return null;
    }

    private static ServerPlayer serverPlayer(VolmitSender sender) {
        ReactPlayer player = sender.player();
        return player != null && player.handle() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    private static ModReactPlatform platform() {
        return (ModReactPlatform) ReactPlatforms.get();
    }
}
