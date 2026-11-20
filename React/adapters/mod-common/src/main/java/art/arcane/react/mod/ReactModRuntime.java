package art.arcane.react.mod;

import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.core.telemetry.TickerDiagnostics;
import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.api.monitor.MonitoringSettings;
import art.arcane.react.api.web.HistoryWebMetricsSource;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.api.web.WebConfigWatcher;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.core.telemetry.RuntimeTelemetry;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.mod.cost.ModCostProbe;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.util.director.ReactDirector;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * React on a mod server, dedicated or the integrated server of a singleplayer world. Each loader forwards server start,
 * end of server tick, server stop and command registration here. Start binds the mod platform, runs every core sampler
 * with React's telemetry and starts React Web. Stop releases all of it, so a client can open and close worlds repeatedly.
 */
public final class ReactModRuntime {
    public static final String ID = "react";
    public static final String NAME = "React";
    private static final List<String> COMMAND_LABELS = List.of("react", "re");
    private static final int CAPTURE_INTERVAL_TICKS = 20;
    private static final Logger LOGGER = LoggerFactory.getLogger(NAME);
    private final String platform;
    private final Path configDirectory;
    private final ModCostProbe costProbe;
    private final ReactDirector director = new ReactDirector();
    private final ModPluginApis pluginApis = new ModPluginApis();
    private static volatile ModTicks pausedTicks;
    private volatile Running running;

    private record Running(ModReactPlatform platform, ModTicks ticks, ExecutorService async, SampleController samples,
                           ReactWebServer web, ModMaps maps, RuntimeTelemetry telemetry, Components components,
                           WebConfigWatcher webConfig, ModSettings settings, ModConfigHotload hotload) {
    }

    /** React's optimisation layer: features, tweaks and actions, started after the samplers they read. */
    private record Components(FeatureController features, TweakController tweaks, ActionController actions) {
        void start() {
            features.start();
            tweaks.start();
            actions.start();
        }

        void postStart() {
            features.postStart();
            tweaks.postStart();
            actions.postStart();
        }

        void stop() {
            quietly("React actions", actions::stop);
            quietly("React tweaks", tweaks::stop);
            quietly("React features", features::stop);
        }
    }

    public ReactModRuntime(String platform, Path configDirectory, ModCostProbe costProbe) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory");
        this.costProbe = Objects.requireNonNull(costProbe, "costProbe");
    }

    public ModCostProbe costProbe() {
        return costProbe;
    }

    public void start(MinecraftServer server) {
        if (running != null) {
            throw new IllegalStateException(NAME + " is already running");
        }
        File dataFolder = configDirectory.resolve(ID).toFile();
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
            LOGGER.error("{} could not create {} and did not start", NAME, dataFolder);
            return;
        }
        ModLanguageHost languageHost = new ModLanguageHost(dataFolder);
        ModTicks ticks = new ModTicks(LOGGER::warn);
        ExecutorService async = Executors.newCachedThreadPool(daemonThreads());
        List<Runnable> startupTasks = new ArrayList<>();
        SampleController samples = new SampleController();
        ReactWebServer[] web = new ReactWebServer[1];
        RuntimeTelemetry telemetry = new RuntimeTelemetry(dataFolder, new RuntimeTelemetry.ServerSources() {
            @Override
            public boolean webAvailable() {
                return web[0] != null;
            }

            @Override
            public int activeWebSocketSessions() {
                ReactWebServer server = web[0];
                return server == null ? 0 : server.activeWebSocketSessions();
            }

            @Override
            public PublishedMetricStore publishedStore() {
                return pluginApis.publishedStore();
            }

            @Override
            public TickerDiagnostics ticker() {
                return ticks.diagnostics();
            }
        });
        HistoryWebMetricsSource metrics = new HistoryWebMetricsSource(() -> samples, telemetry::history, new MetricsSerializer());
        ModMaps maps = new ModMaps(server, dataFolder, ticks, () -> samples);
        ModReactPlatform modPlatform = new ModReactPlatform(platform, server, dataFolder, LOGGER, ticks, async, startupTasks,
                languageHost, costProbe, () -> samples, () -> web[0], maps, telemetry);
        ReactPlatforms.bind(modPlatform);
        Components components;
        List<IController> coreControllers;
        try {
            languageHost.load();
            EntityPriority.bind(languageHost::priority);
            ModWebHost webHost = new ModWebHost(server, dataFolder, platform, metrics, maps, LOGGER::info, () -> samples, telemetry.host(),
                    languageHost::settings);
            web[0] = new ReactWebServer(webHost);
            WebConfigWatcher webConfig = new WebConfigWatcher(new File(dataFolder, "web.toml"), webHost, web[0]);
            components = new Components(new FeatureController(), new TweakController(), new ActionController());
            coreControllers = List.of(components.features(), components.tweaks(), components.actions(), samples,
                    telemetry.jobs(), telemetry.history(), telemetry.jfr());
            ModSettings settings = new ModSettings(server, dataFolder, languageHost, maps, () -> samples, ticks, coreControllers);
            ModConfigHotload hotload = new ModConfigHotload(server, dataFolder, languageHost, maps, () -> samples, modPlatform, ticks);
            running = new Running(modPlatform, ticks, async, samples, web[0], maps, telemetry, components, webConfig, settings, hotload);
        } catch (IOException | RuntimeException failure) {
            EntityPriority.bind(null);
            ReactPlatforms.unbind();
            ticks.close();
            async.shutdownNow();
            LOGGER.error("{} could not load its configuration and did not start", NAME, failure);
            return;
        }
        WebConfigWatcher webConfig = running.webConfig();
        ModSettings settings = running.settings();
        ModConfigHotload hotload = running.hotload();
        costProbe.start(server, ticks::later);
        ticks.every(costProbe::tick);
        try {
            ReactLanguage.initialize();
            for (IController controller : coreControllers) {
                controller.loadConfiguration();
            }
            telemetry.startJobs();
            samples.start();
            modPlatform.entityTicker().start();
            ticks.every(modPlatform.entityTicker()::tick);
            components.start();
            for (Runnable task : startupTasks) {
                task.run();
            }
            startupTasks.clear();
            samples.postStart();
            ticks.every(modPlatform.entityKiller()::tick);
            components.postStart();
            telemetry.start(samples);
            ModPathfinding.bind(telemetry.pathfinding());
            maps.start(modPlatform);
            ticks.every(maps::tick);
            ModSettings.bind(settings);
            MonitoringSettings.bind(languageHost::monitoring);
            ModPlayerMonitors monitors = new ModPlayerMonitors(server, new ModMonitorDisplay(server, ticks, modPlatform.components()));
            ModPlayerMonitors.bind(monitors);
            ticks.every(monitors::tick);
            hotload.start();
            ticks.every(hotload::tick);
            modPlatform.ownerCost().start();
            modPlatform.modScheduler().markReady();
            web[0].start();
            web[0].postStart();
            webConfig.start();
            pluginApis.start(modPlatform, dataFolder, ticks);
            int[] untilCapture = {CAPTURE_INTERVAL_TICKS};
            ticks.every(() -> {
                if (--untilCapture[0] <= 0) {
                    untilCapture[0] = CAPTURE_INTERVAL_TICKS;
                    modPlatform.ownerCost().publish();
                    modPlatform.chunkSamples().decayIfDue(System.currentTimeMillis());
                }
            });
        } catch (RuntimeException failure) {
            LOGGER.error("{} failed to start on {}", NAME, platform, failure);
            stop();
            return;
        }
        pausedTicks = ticks;
        LOGGER.info("{} started on {} with {} samplers; configuration in {}", NAME, platform, samples.getSamplers().size(), dataFolder);
    }

    public void tick() {
        Running active = running;
        if (active != null) {
            SamplerEventCounters.get().count(SamplerEventCounters.Counter.SERVER_TICK, null);
            active.ticks().tick();
        }
    }

    /** Runs React's queued server-thread work while an empty server is paused. */
    public static void tickWhilePaused() {
        ModTicks ticks = pausedTicks;
        if (ticks != null) {
            ticks.tick();
        }
    }

    public void stop() {
        Running active = running;
        running = null;
        pausedTicks = null;
        if (active == null) {
            return;
        }
        try {
            quietly("React config hotload", active.hotload()::stop);
            quietly("React player monitors", () -> ModPlayerMonitors.current().ifPresent(ModPlayerMonitors::stop));
            MonitoringSettings.bind(null);
            ModSettings.unbind(active.settings());
            quietly("React components", active.platform().components()::disable);
            active.components().stop();
            quietly("React entity state", active.platform().entityTicker()::stop);
            EntityPriority.bind(null);
            quietly("React entity cleanup", active.platform().entityKiller()::stopAll);
            quietly("React server hooks", ReactModRuntime::clearHosts);
            quietly("React scheduler", active.platform().modScheduler()::deactivate);
            quietly("React Web configuration", active.webConfig()::stop);
            quietly("React owner costs", active.platform().ownerCost()::stop);
            quietly("React cost probe", costProbe::stop);
            quietly("React maps", active.maps()::stop);
            quietly("React Web", active.web()::stop);
            quietly("React API packs", pluginApis::stop);
            ModPathfinding.bind(null);
            quietly("React telemetry", () -> active.telemetry().stop(active.samples()));
            quietly("Samplers", active.samples()::stop);
            quietly("React language", ReactLanguage::close);
            quietly("React ticks", active.ticks()::close);
        } finally {
            active.async().shutdownNow();
            try {
                active.async().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            ReactPlatforms.unbind();
        }
    }

    private static void clearHosts() {
        ModHopperHost.clear();
        ModChunkGeneration.clear();
        ModDropHost.clear();
        ModPlayerHost.clear();
        ModBlockHost.clear();
        ModChunkHost.clear();
        ModSpawnHost.clear();
        ModRedstoneHost.clear();
        ModGrowthHost.clear();
        ModFurnaceHost.clear();
        ModExplosionHost.clear();
        ModEntityActivityHost.clear();
        ModMobStackHost.clear();
        ModItemHost.clear();
        ModEntityEventHost.clear();
    }

    private static void quietly(String part, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            LOGGER.warn("{} did not stop cleanly", part, failure);
        }
    }

    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String label : COMMAND_LABELS) {
            dispatcher.register(Commands.literal(label)
                    .executes((CommandContext<CommandSourceStack> context) -> run(context.getSource(), label, ""))
                    .then(Commands.argument("arguments", StringArgumentType.greedyString())
                            .suggests((CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> suggest(context, builder, label))
                            .executes((CommandContext<CommandSourceStack> context) ->
                                    run(context.getSource(), label, StringArgumentType.getString(context, "arguments")))));
        }
    }

    private int run(CommandSourceStack source, String label, String arguments) {
        if (running == null) {
            source.sendFailure(Component.literal(NAME + " is not running on this server."));
            return 0;
        }
        director.execute(new ModCommandSender(source), label, words(arguments));
        return 1;
    }

    private CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder, String label) {
        if (running == null) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemaining();
        SuggestionsBuilder last = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        for (String option : director.complete(new ModCommandSender(context.getSource()), label, remaining.split(" ", -1))) {
            last.suggest(option);
        }
        return last.buildFuture();
    }

    /** The words of a command, split as a Bukkit command receives them. */
    static String[] words(String text) {
        return text.isEmpty() ? new String[0] : text.split(" ", -1);
    }

    private static ThreadFactory daemonThreads() {
        AtomicInteger sequence = new AtomicInteger();
        return (Runnable task) -> {
            Thread thread = new Thread(task, "React Async " + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
