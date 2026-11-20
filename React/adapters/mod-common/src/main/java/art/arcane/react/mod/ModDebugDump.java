package art.arcane.react.mod;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.directorcommand.DebugDump;
import art.arcane.react.content.directorcommand.DebugReport;
import art.arcane.react.content.directorcommand.EntityDataCommands;
import art.arcane.react.content.sampler.SamplerTicksPerSecond;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.mod.cost.CostRegistry;
import art.arcane.react.mod.cost.ModOwners;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.SamplerHost;
import art.arcane.react.platform.TickStatsHost;
import art.arcane.volmlib.util.web.MclogsClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * The mod server's side of {@code /react debug dump} and {@code /react debug entity-data}. Mods track no per-entity
 * priority, so entity-data reports the values Bukkit shows for an entity React has not scored yet.
 */
final class ModDebugDump implements DebugDump.Host {
    private static final double UNTRACKED_PRIORITY = 100D;
    private static final double UNTRACKED_CROWDING = 1D;
    private static final double UNTRACKED_NEAREST_PLAYER = 1D;
    private static final int LOOK_STEPS = 16;
    private static final double LOOK_RADIUS = 2D;
    private static final int GLOW_TICKS = 20;
    private final MinecraftServer server;

    ModDebugDump(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public DebugReport.Snapshot capture(ReactCommandSender sender) {
        ReactPlatform platform = ReactPlatforms.get();
        return new DebugReport.Snapshot(Instant.now(), ReactModRuntime.NAME, ModCommandHost.reactVersion(), platform.ownerNounTitle(),
                sender.isPlayer() ? "player" : "console-or-other", serverFields(platform), owners(),
                platform.dataFolder().toPath(), codeSource());
    }

    @Override
    public String details() {
        boolean monitoringOnly = ReactPlatforms.getOrNull() instanceof ModReactPlatform platform && platform.components().isMonitoringOnly();
        return "Ready: true\nMonitoring only: " + monitoringOnly + "\nLanguage: " + ReactLanguage.activeLocale()
                + "\nLanguage source: " + ReactLanguage.sourceState();
    }

    @Override
    public boolean onServerThread(Runnable task) {
        return ReactPlatforms.get().scheduler().global(task, 0);
    }

    @Override
    public boolean async(Runnable task) {
        try {
            ReactPlatforms.get().asyncExecutor().execute(task);
            return true;
        } catch (RejectedExecutionException rejected) {
            return false;
        }
    }

    @Override
    public URI upload(String report, String title, String source) throws IOException, InterruptedException {
        return new MclogsClient().publish(report, title, source);
    }

    @Override
    public void info(String message) {
        ReactPlatforms.get().info(message);
    }

    @Override
    public void warn(String message, Throwable failure) {
        ReactPlatforms.get().warn(message, failure);
    }

    private List<DebugReport.Field> serverFields(ReactPlatform platform) {
        PlayerList players = server.getPlayerList();
        List<DebugReport.Field> fields = new ArrayList<>();
        fields.add(DebugReport.Field.of("Implementation", server.getServerModName()));
        fields.add(DebugReport.Field.of("Scheduler", "Minecraft server thread"));
        fields.add(DebugReport.Field.of("Server version", platform.platformName() + " " + server.getServerVersion()));
        fields.add(DebugReport.Field.of("Minecraft", VanillaApi.minecraftVersion()));
        fields.add(DebugReport.Field.of("Online mode", server.usesAuthentication()));
        fields.add(DebugReport.Field.of("Players online", server.getPlayerCount()));
        fields.add(DebugReport.Field.of("Maximum players", players.getMaxPlayers()));
        fields.add(DebugReport.Field.of("View distance", players.getViewDistance()));
        fields.add(DebugReport.Field.of("Simulation distance", players.getSimulationDistance()));
        fields.add(DebugReport.Field.of("Hardcore", server.isHardcore()));
        fields.add(DebugReport.Field.of("Allow flight", VanillaApi.allowFlight(server)));
        fields.add(DebugReport.Field.of("Whitelist enabled", players.isUsingWhitelist()));
        fields.add(DebugReport.Field.of("Default game mode", server.getDefaultGameType().name()));
        fields.add(DebugReport.Field.of("Idle timeout minutes", VanillaApi.idleTimeoutMinutes(server)));
        fields.add(DebugReport.Field.of("Pending scheduler tasks", pendingTasks(platform)));
        Map<String, Integer> dimensions = new TreeMap<>();
        int worlds = 0;
        for (ServerLevel level : server.getAllLevels()) {
            worlds++;
            dimensions.merge(VanillaApi.dimensionId(level.dimension()), 1, Integer::sum);
        }
        fields.add(DebugReport.Field.of("Loaded worlds", worlds));
        for (Map.Entry<String, Integer> dimension : dimensions.entrySet()) {
            fields.add(DebugReport.Field.of("Worlds " + dimension.getKey().toLowerCase(Locale.ROOT), dimension.getValue()));
        }
        fields.add(DebugReport.Field.of("TPS", ticksPerSecond(platform)));
        fields.add(DebugReport.Field.of("MSPT", String.format(Locale.ROOT, "%.2f", VanillaApi.averageTickMillis(server))));
        return fields;
    }

    private static String pendingTasks(ReactPlatform platform) {
        Optional<TickStatsHost> ticks = platform.tickStatsHost();
        if (ticks.isEmpty()) {
            return "unavailable";
        }
        try {
            return Integer.toString(ticks.get().pendingSchedulerTasks());
        } catch (UnsupportedOperationException unsupported) {
            return "unavailable";
        }
    }

    private static String ticksPerSecond(ReactPlatform platform) {
        Optional<SampleController> samples = platform.samplerHost().flatMap(SamplerHost::sampleController);
        Sampler sampler = samples.isEmpty() ? null : samples.get().getSampler(SamplerTicksPerSecond.ID);
        if (sampler == null || !sampler.isSampleAvailable()) {
            return "unavailable";
        }
        return String.format(Locale.ROOT, "%.2f", sampler.sample());
    }

    private static List<DebugReport.Owner> owners() {
        List<DebugReport.Owner> owners = new ArrayList<>();
        for (ModOwners.LoadedMod mod : CostRegistry.loadedMods()) {
            owners.add(new DebugReport.Owner(mod.id(), mod.version(), List.of(DebugReport.Field.of("name", mod.name()))));
        }
        return owners;
    }

    private static Path codeSource() {
        try {
            CodeSource source = ModDebugDump.class.getProtectionDomain().getCodeSource();
            return source == null ? null : Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException unresolved) {
            return null;
        }
    }

    static void entityData(ServerPlayer player, Object target) {
        Vec3 look = player.getLookAngle();
        Vec3 point = player.position().add(look);
        ServerLevel level = VanillaApi.level(player);
        for (int step = 0; step < LOOK_STEPS; step++) {
            point = point.add(look);
            AABB box = new AABB(point.x - LOOK_RADIUS, point.y - LOOK_RADIUS, point.z - LOOK_RADIUS,
                    point.x + LOOK_RADIUS, point.y + LOOK_RADIUS, point.z + LOOK_RADIUS);
            List<Entity> entities = level.getEntities(player, box, (Entity entity) -> true);
            if (entities.isEmpty()) {
                continue;
            }
            Entity entity = entities.get(0);
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
            }
            Optional<EntityHost> host = ReactPlatforms.get().entityHost();
            double priority = host.isEmpty() ? UNTRACKED_PRIORITY : host.get().priority(new ModGameEntity(entity));
            EntityDataCommands.send(target, priority, UNTRACKED_CROWDING, UNTRACKED_NEAREST_PLAYER, 0L);
            return;
        }
    }
}
