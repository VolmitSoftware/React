package art.arcane.react.mod;

import art.arcane.react.mod.cost.ModCostProbe;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Temporary console check, {@code /react modcost}: activates the cost probe for one 100-tick window and prints the
 * most expensive mods. The core cost samplers replace it.
 */
final class ModCostCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReactModRuntime.NAME);
    private static final int WINDOW_TICKS = 100;
    private static final int ROWS = 15;
    private final ModCostProbe probe;
    private final Supplier<ModTicks> ticks;
    private ModTicks measuring;

    ModCostCheck(ModCostProbe probe, Supplier<ModTicks> ticks) {
        this.probe = Objects.requireNonNull(probe, "probe");
        this.ticks = Objects.requireNonNull(ticks, "ticks");
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher, List<String> labels) {
        for (String label : labels) {
            dispatcher.register(Commands.literal(label).then(Commands.literal("modcost")
                    .executes((CommandContext<CommandSourceStack> context) -> measure(context.getSource()))));
        }
    }

    private int measure(CommandSourceStack source) {
        ModTicks serverTicks = ticks.get();
        if (serverTicks == null || measuring == serverTicks) {
            source.sendFailure(Component.literal(serverTicks == null ? ReactModRuntime.NAME + " is not running" : "Mod cost is already being measured"));
            return 0;
        }
        measuring = serverTicks;
        boolean wasActive = probe.active();
        probe.setActive(true);
        probe.drain((String id, String name, long nanos, long sync, long async) -> { });
        long started = System.nanoTime();
        int[] remaining = {WINDOW_TICKS};
        AutoCloseable[] task = new AutoCloseable[1];
        task[0] = serverTicks.every(() -> {
            if (--remaining[0] > 0) {
                return;
            }
            measuring = null;
            List<ModCostProbe.OwnerCost> costs = new ArrayList<>();
            probe.drainInto(costs);
            if (!wasActive) {
                probe.setActive(false);
            }
            report(source, costs, (System.nanoTime() - started) / 1.0e9);
            try {
                task[0].close();
            } catch (Exception failure) {
                LOGGER.warn("Could not stop the mod cost window", failure);
            }
        });
        source.sendSuccess(() -> Component.literal("Measuring mod cost for " + WINDOW_TICKS + " ticks"), false);
        return 1;
    }

    private void report(CommandSourceStack source, List<ModCostProbe.OwnerCost> costs, double seconds) {
        costs.sort(ModCostProbe.OwnerCost.MOST_EXPENSIVE);
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "Mod cost over %.1f s (ms/s, sync calls, async calls)", seconds));
        String problem = probe.eventAttributionProblem();
        if (problem != null) {
            text.append("\nEvent listeners not attributed: ").append(problem);
        }
        for (ModCostProbe.OwnerCost cost : costs.subList(0, Math.min(ROWS, costs.size()))) {
            text.append(String.format(Locale.ROOT, "\n  %-20s %-24s %8.3f %9d %7d", cost.ownerId(), cost.displayName(),
                    cost.exclusiveNanos() / 1.0e6 / seconds, cost.syncCalls(), cost.asyncCalls()));
        }
        String report = text.toString();
        source.sendSuccess(() -> Component.literal(report), false);
    }
}
