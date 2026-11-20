package art.arcane.react.mod;

import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.telemetry.timing.WorldTickSeries;
import art.arcane.react.platform.timing.SaveTimings;
import art.arcane.react.platform.timing.WorldTickTimes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Measured tick time of every dimension and the duration of full saves, fed by React's level and server mixins.
 * Each dimension also gets its own {@code world-tick-} sampler.
 */
public final class ModWorldTimings {
    private static volatile ModWorldTimings active;
    private final MinecraftServer server;
    private final Supplier<SampleController> samples;
    private final WorldTickTimes ticks = new WorldTickTimes();
    private final SaveTimings saves = new SaveTimings(System::currentTimeMillis);
    private final WorldTickSeries series = new WorldTickSeries(ticks);
    private final List<WorldTickTimes.World> drained = new ArrayList<>();

    ModWorldTimings(MinecraftServer server, Supplier<SampleController> samples) {
        this.server = server;
        this.samples = samples;
    }

    WorldTickTimes ticks() {
        return ticks;
    }

    SaveTimings saves() {
        return saves;
    }

    void start() {
        active = this;
    }

    void stop() {
        if (active == this) {
            active = null;
        }
        series.clear(samples.get());
        ticks.clear();
        saves.clear();
    }

    /** Server thread, once a second. */
    void publish() {
        drained.clear();
        for (ServerLevel level : server.getAllLevels()) {
            if ((Object) level instanceof ModLevelTiming timing) {
                drained.add(timing.react$drainTicks(ModReactWorld.idOf(level), ModReactWorld.nameOf(level)));
            }
        }
        ticks.publish(drained);
        if (ticks.measuring()) {
            series.sync(samples.get());
        }
    }

    public static void saveStarted(boolean manual) {
        ModWorldTimings timings = active;
        if (timings != null) {
            timings.saves.begin(manual ? SaveTimings.Kind.MANUAL : SaveTimings.Kind.AUTOSAVE, System.nanoTime());
        }
    }

    public static void saveFinished() {
        ModWorldTimings timings = active;
        if (timings != null) {
            timings.saves.end(System.nanoTime());
        }
    }

    public static void levelSaved(ServerLevel level, long elapsedNanos) {
        ModWorldTimings timings = active;
        if (timings != null) {
            timings.saves.worldSaved(ModReactWorld.nameOf(level), elapsedNanos);
        }
    }
}
