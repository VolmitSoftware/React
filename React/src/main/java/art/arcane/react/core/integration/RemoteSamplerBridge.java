package art.arcane.react.core.integration;

import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteSamplerBridge {
    private final Map<String, Map<String, IntegrationMetricSample>> samplesByPlugin = new ConcurrentHashMap<>();

    public void updatePluginSamples(
            String pluginId,
            Set<String> expectedKeys,
            Map<String, IntegrationMetricSample> samples,
            String unavailableReason
    ) {
        String normalizedPlugin = normalizePlugin(pluginId);
        Map<String, IntegrationMetricSample> pluginSamples = samplesByPlugin.computeIfAbsent(normalizedPlugin, ignored -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        Set<String> safeExpectedKeys = expectedKeys == null ? Set.of() : expectedKeys;
        Map<String, IntegrationMetricSample> safeSamples = samples == null ? Map.of() : samples;
        String reason = unavailableReason == null || unavailableReason.isBlank() ? "unavailable" : unavailableReason;

        for (String key : safeExpectedKeys) {
            IntegrationMetricSample sample = safeSamples.get(key);
            if (sample != null) {
                pluginSamples.put(key, sample);
                continue;
            }

            pluginSamples.put(key, IntegrationMetricSample.unavailable(
                    IntegrationMetricSchema.descriptor(key),
                    reason,
                    now
            ));
        }

        for (Map.Entry<String, IntegrationMetricSample> entry : safeSamples.entrySet()) {
            pluginSamples.put(entry.getKey(), entry.getValue());
        }
    }

    public void markPluginUnavailable(String pluginId, Set<String> keys, String reason) {
        updatePluginSamples(pluginId, keys, Map.of(), reason);
    }

    public IntegrationMetricSample getSample(String pluginId, String key) {
        String normalizedPlugin = normalizePlugin(pluginId);
        Map<String, IntegrationMetricSample> pluginSamples = samplesByPlugin.get(normalizedPlugin);
        if (pluginSamples == null) {
            return IntegrationMetricSample.unavailable(
                    IntegrationMetricSchema.descriptor(key),
                    "plugin-not-discovered",
                    System.currentTimeMillis()
            );
        }

        IntegrationMetricSample sample = pluginSamples.get(key);
        if (sample != null) {
            return sample;
        }

        return IntegrationMetricSample.unavailable(
                IntegrationMetricSchema.descriptor(key),
                "metric-not-published",
                System.currentTimeMillis()
        );
    }

    public double valueOr(String pluginId, String key, double fallback) {
        return getSample(pluginId, key).valueOr(fallback);
    }

    public boolean isAvailable(String pluginId, String key) {
        return getSample(pluginId, key).available();
    }

    public Map<String, IntegrationMetricSample> snapshot(String pluginId) {
        String normalizedPlugin = normalizePlugin(pluginId);
        Map<String, IntegrationMetricSample> samples = samplesByPlugin.get(normalizedPlugin);
        if (samples == null) {
            return Map.of();
        }
        return new HashMap<>(samples);
    }

    private String normalizePlugin(String pluginId) {
        return pluginId == null ? "" : pluginId.trim().toLowerCase(Locale.ROOT);
    }
}
