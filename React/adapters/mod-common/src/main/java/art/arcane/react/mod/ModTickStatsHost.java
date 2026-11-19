package art.arcane.react.mod;

import art.arcane.react.content.sampler.TickTimeSource;
import art.arcane.react.platform.TickStatsHost;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/** Tick work times straight from {@link MinecraftServer}'s 100-tick ring, read at the end of each server tick. */
final class ModTickStatsHost implements TickStatsHost {
    private static final long[] EMPTY_TICKS = new long[0];
    private final MinecraftServer server;
    private final ModTicks ticks;

    ModTickStatsHost(MinecraftServer server, ModTicks ticks) {
        this.server = server;
        this.ticks = ticks;
    }

    @Override
    public Optional<TickTimeSource> tickTimeSource() {
        return Optional.of(new VanillaTickTimes(server));
    }

    @Override
    public int pendingSchedulerTasks() {
        return ticks.pending();
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
            return capture(server.getTickTimesNanos(), server.getTickCount());
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
