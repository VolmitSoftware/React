package art.arcane.react.mod;

import art.arcane.react.platform.timing.WorldTickTimes;

import java.util.UUID;

/** Tick timer that React's mixin adds to every server level. Read and reset on the server thread. */
public interface ModLevelTiming {
    /** The ticks timed since the last call, which starts a new window. */
    WorldTickTimes.World react$drainTicks(UUID id, String key);
}
