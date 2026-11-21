package art.arcane.react.mod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Per-owner rates averaged over the last few seconds, for the share pies: each fold keeps {@link #KEEP} of the previous
 * value and adds the rest from the new one, so a pie neither flickers with each second nor holds a stale owner long.
 */
final class RollingShare {
    static final double KEEP = 0.8D;
    private static final double FLOOR = 1.0E-4D;
    private final Map<String, Double> values = new ConcurrentHashMap<>();

    /** Server thread, once a published window. */
    void fold(Map<String, Double> sample) {
        values.replaceAll((String owner, Double value) -> value * KEEP);
        for (Map.Entry<String, Double> entry : sample.entrySet()) {
            double value = entry.getValue() == null ? 0D : entry.getValue();
            if (value > 0D) {
                values.merge(entry.getKey(), value * (1D - KEEP), Double::sum);
            }
        }
        values.values().removeIf((Double value) -> value < FLOOR);
    }

    void clear() {
        values.clear();
    }

    /** Pie buckets by display name, each value times {@code scale} and at least 1. */
    Map<String, Long> buckets(double scale, UnaryOperator<String> names) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            buckets.merge(names.apply(entry.getKey()), Math.max(1L, Math.round(entry.getValue() * scale)), Long::sum);
        }
        return buckets;
    }
}
