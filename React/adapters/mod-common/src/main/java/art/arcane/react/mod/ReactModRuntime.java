package art.arcane.react.mod;

import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.localization.ReactLanguage;
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
 * React on a dedicated mod server. Each loader forwards server start, end of server tick, server stop and command
 * registration here. Start binds the mod platform, runs every core sampler and starts React Web.
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
    private volatile Running running;

    private record Running(ModReactPlatform platform, ModTicks ticks, ExecutorService async, SampleController samples,
                           ReactWebServer web) {
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
        if (!server.isDedicatedServer()) {
            return;
        }
        File dataFolder = configDirectory.resolve(ID).toFile();
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
            LOGGER.error("{} could not create {} and did not start", NAME, dataFolder);
            return;
        }
        ModLanguageHost languageHost;
        try {
            languageHost = new ModLanguageHost(dataFolder);
        } catch (IOException failure) {
            LOGGER.error("{} could not read react.toml and did not start", NAME, failure);
            return;
        }
        ModTicks ticks = new ModTicks(LOGGER::warn);
        ExecutorService async = Executors.newCachedThreadPool(daemonThreads());
        List<Runnable> startupTasks = new ArrayList<>();
        SampleController samples = new SampleController();
        ModWebMetricsSource metrics = new ModWebMetricsSource(() -> samples);
        ReactWebServer[] web = new ReactWebServer[1];
        ModReactPlatform modPlatform = new ModReactPlatform(platform, server, dataFolder, LOGGER, ticks, async, startupTasks,
                languageHost, costProbe, () -> samples, () -> web[0]);
        ReactPlatforms.bind(modPlatform);
        web[0] = new ReactWebServer(new ModWebHost(server, dataFolder, platform, metrics, LOGGER::info));
        running = new Running(modPlatform, ticks, async, samples, web[0]);
        costProbe.start(server, ticks::later);
        ticks.every(costProbe::tick);
        try {
            ReactLanguage.initialize();
            samples.start();
            for (Runnable task : startupTasks) {
                task.run();
            }
            startupTasks.clear();
            samples.postStart();
            modPlatform.ownerCost().start();
            modPlatform.modScheduler().markReady();
            web[0].start();
            web[0].postStart();
            int[] untilCapture = {CAPTURE_INTERVAL_TICKS};
            ticks.every(() -> {
                if (--untilCapture[0] <= 0) {
                    untilCapture[0] = CAPTURE_INTERVAL_TICKS;
                    modPlatform.ownerCost().publish();
                    async.execute(metrics::capture);
                }
            });
        } catch (RuntimeException failure) {
            LOGGER.error("{} failed to start on {}", NAME, platform, failure);
            stop();
            return;
        }
        LOGGER.info("{} started on {} with {} samplers; configuration in {}", NAME, platform, samples.getSamplers().size(), dataFolder);
    }

    public void tick() {
        Running active = running;
        if (active != null) {
            SamplerEventCounters.get().count(SamplerEventCounters.Counter.SERVER_TICK, null);
            active.ticks().tick();
        }
    }

    public void stop() {
        Running active = running;
        running = null;
        if (active == null) {
            return;
        }
        active.platform().modScheduler().deactivate();
        active.platform().ownerCost().stop();
        costProbe.stop();
        try {
            active.web().stop();
        } catch (RuntimeException failure) {
            LOGGER.warn("React Web did not stop cleanly", failure);
        }
        try {
            active.samples().stop();
        } catch (RuntimeException failure) {
            LOGGER.warn("Samplers did not stop cleanly", failure);
        }
        ReactLanguage.close();
        active.ticks().close();
        active.async().shutdownNow();
        try {
            active.async().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        ReactPlatforms.unbind();
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
