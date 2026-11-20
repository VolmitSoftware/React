package art.arcane.react.mod;

import art.arcane.react.content.feature.AnimusMetrics;
import art.arcane.react.platform.PlatformLog;
import art.arcane.react.platform.PlatformScheduler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Animus's {@code animus.*} metrics on a mod server, read once a second from its metric catalog. On Bukkit Animus
 * pushes the same values through React's metric API.
 */
final class ModAnimusMetrics {
    private static final String METRICS = "art.arcane.animus.core.metrics.AnimusMetrics";
    private static final String CATALOG = "art.arcane.animus.core.metrics.ReactMetricCatalog";
    private static final int REFRESH_TICKS = 20;
    private final Class<?> metrics;
    private final Class<?> catalog;
    private volatile AnimusMetrics.Snapshot snapshot = AnimusMetrics.Snapshot.empty();
    private boolean failureLogged;

    private ModAnimusMetrics(Class<?> metrics, Class<?> catalog) {
        this.metrics = metrics;
        this.catalog = catalog;
    }

    /** Null when Animus is not installed. */
    static ModAnimusMetrics installed() {
        Class<?> metrics = ModReflection.find(METRICS);
        Class<?> catalog = ModReflection.find(CATALOG);
        return metrics == null || catalog == null ? null : new ModAnimusMetrics(metrics, catalog);
    }

    AnimusMetrics.Snapshot snapshot() {
        return snapshot;
    }

    void schedule(PlatformScheduler scheduler) {
        scheduler.global(() -> {
            refresh();
            schedule(scheduler);
        }, REFRESH_TICKS);
    }

    private void refresh() {
        try {
            Object current = ModReflection.callStatic(metrics, "current");
            Object values = ModReflection.callStatic(catalog, "values", ModReflection.call(current, "snapshot"));
            Map<String, Double> readings = new LinkedHashMap<>();
            if (values instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String key && entry.getValue() instanceof Number number) {
                        readings.put(key, number.doubleValue());
                    }
                }
            }
            snapshot = AnimusMetrics.parse(true, readings);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            snapshot = AnimusMetrics.Snapshot.empty();
            if (!failureLogged) {
                failureLogged = true;
                PlatformLog.warn("Could not read Animus metrics: " + failure);
            }
        }
    }
}
