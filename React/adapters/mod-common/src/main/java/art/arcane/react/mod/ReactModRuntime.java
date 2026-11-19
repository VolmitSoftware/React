package art.arcane.react.mod;

import art.arcane.react.mod.cost.ModCostProbe;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * React on a mod server. Each loader forwards server start, end of server tick, server stop and command registration
 * here; the shared runtime from core plugs in behind these entry points.
 */
public final class ReactModRuntime {
    public static final String ID = "react";
    public static final String NAME = "React";
    private static final List<String> COMMAND_LABELS = List.of("react", "re");
    private static final Logger LOGGER = LoggerFactory.getLogger(NAME);
    private final String platform;
    private final Path configDirectory;
    private final ModCostProbe costProbe;
    private final ModCostCheck modCostCheck;
    private volatile Running running;

    private record Running(MinecraftServer server, ModTicks ticks) {
    }

    public ReactModRuntime(String platform, Path configDirectory, ModCostProbe costProbe) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory");
        this.costProbe = Objects.requireNonNull(costProbe, "costProbe");
        this.modCostCheck = new ModCostCheck(costProbe, () -> {
            Running active = running;
            return active == null ? null : active.ticks();
        });
    }

    public ModCostProbe costProbe() {
        return costProbe;
    }

    public void start(MinecraftServer server) {
        if (running != null) {
            throw new IllegalStateException(NAME + " is already running");
        }
        ModTicks ticks = new ModTicks(LOGGER::warn);
        running = new Running(server, ticks);
        costProbe.start(server, ticks::later);
        ticks.every(costProbe::tick);
        LOGGER.info("{} started on {} with configuration in {}", NAME, platform, configDirectory);
    }

    public void tick() {
        Running active = running;
        if (active != null) {
            active.ticks().tick();
        }
    }

    public void stop() {
        Running active = running;
        running = null;
        costProbe.stop();
        if (active != null) {
            active.ticks().close();
        }
    }

    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        modCostCheck.register(dispatcher, COMMAND_LABELS);
        for (String label : COMMAND_LABELS) {
            dispatcher.register(Commands.literal(label)
                    .executes((CommandContext<CommandSourceStack> context) -> run(context.getSource(), ""))
                    .then(Commands.argument("arguments", StringArgumentType.greedyString())
                            .executes((CommandContext<CommandSourceStack> context) ->
                                    run(context.getSource(), StringArgumentType.getString(context, "arguments")))));
        }
    }

    private int run(CommandSourceStack source, String arguments) {
        String shown = arguments.isBlank() ? "" : " (" + arguments + ")";
        source.sendSuccess(() -> Component.literal(NAME + " on " + platform + " has no commands yet" + shown), false);
        return 1;
    }
}
