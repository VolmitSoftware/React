package art.arcane.react.mod;

import art.arcane.react.content.sampler.TickTimeSource;
import art.arcane.react.platform.TickStatsHost;
import art.arcane.react.platform.timing.SaveTimings;
import art.arcane.react.platform.timing.WorldTickTimes;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/**
 * Tick work times straight from {@link MinecraftServer}'s 100-tick ring, read at the end of each server tick, plus
 * each dimension's measured tick time and full save durations.
 */
final class ModTickStatsHost implements TickStatsHost {
    private static final long[] EMPTY_TICKS = new long[0];
    private final MinecraftServer server;
    private final ModTicks ticks;
    private final ModWorldTimings worldTimings;

    ModTickStatsHost(MinecraftServer server, ModTicks ticks, ModWorldTimings worldTimings) {
        this.server = server;
        this.ticks = ticks;
        this.worldTimings = worldTimings;
    }

    @Override
    public Optional<TickTimeSource> tickTimeSource() {
        return Optional.of(new VanillaTickTimes(server));
    }

    @Override
    public int pendingSchedulerTasks() {
        return ticks.pending();
    }

    @Override
    public Optional<WorldTickTimes> worldTickTimes() {
        return Optional.of(worldTimings.ticks());
    }

    @Override
    public Optional<SaveTimings> saveTimings() {
        return Optional.of(worldTimings.saves());
    }

    /** The ring slot for tick {@code n} is {@code n % 100}, written before the end-of-tick event fires. */
    static final class VanillaTickTimes implements TickTimeSource {
        private final MinecraftServer server;
        private boolean hasBaseline;
        private int lastTick;
        private int freshTicks;

        VanillaTickTimes(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public long[] read() {
            return capture(VanillaForms.tickTimesNanos(server), server.getTickCount());
        }

        @Override
        public int freshTicks(int observedTicks) {
            return freshTicks;
        }

        long[] capture(long[] ring, int completed) {
            int valid = Math.min(ring.length, Math.max(0, completed));
            freshTicks = hasBaseline ? Math.min(valid, Math.max(0, completed - lastTick)) : 0;
            hasBaseline = true;
            lastTick = completed;
            if (valid == 0) {
                return EMPTY_TICKS;
            }
            long[] ordered = new long[valid];
            int oldest = completed - valid + 1;
            for (int index = 0; index < valid; index++) {
                ordered[index] = ring[Math.floorMod(oldest + index, ring.length)];
            }
            return ordered;
        }
    }
}
