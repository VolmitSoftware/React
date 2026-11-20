package art.arcane.react.mod;

import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.api.protect.internal.ModProtectionEngine;
import art.arcane.react.core.integration.ModApiRegistrations;
import art.arcane.react.core.integration.ThirdPartyMetricRegistry;
import art.arcane.react.core.pluginapi.PluginApiPacks;
import art.arcane.react.platform.PlatformLog;

import java.io.File;

/**
 * React's public API on a mod server: metric sources and protection providers registered through
 * {@code ReactModApi}, {@code ReactMetrics} publishing and host reads, {@code ReactModProtection}, and the mod API
 * pack catalog behind {@code /react plugin-api}.
 */
final class ModPluginApis {
    private static final int TICK_INTERVAL = 20;
    private static final int PROTECTION_REFRESH_SECONDS = 5;
    private volatile PluginApiPacks packs;
    private volatile ThirdPartyMetricRegistry metrics;
    private volatile ModProtectionEngine protection;

    void start(ModReactPlatform platform, File dataFolder, ModTicks ticks) {
        ThirdPartyMetricRegistry registry = new ThirdPartyMetricRegistry(ModApiRegistrations::metricSources);
        registry.install();
        metrics = registry;
        ModProtectionEngine engine = new ModProtectionEngine(new ModProtectionEntities(), PlatformLog::warn);
        engine.reconcile(ModApiRegistrations.protectionProviders());
        engine.install();
        protection = engine;
        PluginApiPacks started = new PluginApiPacks(new ModPluginApiHost(platform::integrations));
        started.start(dataFolder.toPath());
        packs = started;
        int[] countdown = {TICK_INTERVAL};
        int[] seconds = {0};
        ticks.every(() -> {
            if (--countdown[0] > 0) {
                return;
            }
            countdown[0] = TICK_INTERVAL;
            long now = System.currentTimeMillis();
            registry.reconcile(now);
            if (++seconds[0] % PROTECTION_REFRESH_SECONDS == 0) {
                engine.reconcile(ModApiRegistrations.protectionProviders());
            }
            started.tick();
        });
        registry.reconcile(System.currentTimeMillis());
        started.requestReload();
    }

    PublishedMetricStore publishedStore() {
        ThirdPartyMetricRegistry current = metrics;
        return current == null ? null : current.store();
    }

    void stop() {
        PluginApiPacks currentPacks = packs;
        ThirdPartyMetricRegistry currentMetrics = metrics;
        ModProtectionEngine currentProtection = protection;
        packs = null;
        metrics = null;
        protection = null;
        if (currentPacks != null) {
            currentPacks.stop();
        }
        if (currentMetrics != null) {
            currentMetrics.uninstall();
        }
        if (currentProtection != null) {
            currentProtection.uninstall();
        }
    }
}
