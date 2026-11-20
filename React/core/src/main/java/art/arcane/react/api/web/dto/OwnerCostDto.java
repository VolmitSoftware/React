package art.arcane.react.api.web.dto;

import java.util.Map;

/**
 * Per-owner server-thread cost: every loaded plugin or mod, measured or not, from most to least expensive.
 */
public class OwnerCostDto {
    /** {@code plugin} or {@code mod}. */
    public String ownerNoun;
    /** Whether the last window is fresh; false means the probe is idle and only {@code recentMsPerSecond} is set. */
    public boolean measuring;
    public double windowSeconds;
    public long publishedAtMs;
    public double totalMsPerSecond;
    public KindDto[] kinds;
    public OwnerDto[] owners;

    public static class KindDto {
        /** {@code event}, {@code block_entity}, {@code entity}, {@code worldgen}, or another kind a platform reports. */
        public String kind;
        public String label;
        public double msPerSecond;
        /** Fraction of {@code totalMsPerSecond}, 0 to 1. */
        public double share;
    }

    public static class OwnerDto {
        public String id;
        /** The owner's history sampler, {@code plugin-<id>} or {@code mod-<id>}; null when it has none, as React itself. */
        public String samplerId;
        public String name;
        public double msPerSecond;
        /** Smoothed ms/s that keeps its last value while nothing measures. */
        public double recentMsPerSecond;
        public int calls;
        /** Fraction of {@code totalMsPerSecond}, 0 to 1. */
        public double share;
        /** ms/s per kind id; kinds with no cost are left out. */
        public Map<String, Double> kinds;
    }
}
