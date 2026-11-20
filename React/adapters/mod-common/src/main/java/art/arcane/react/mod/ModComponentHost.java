package art.arcane.react.mod;

import art.arcane.react.core.controller.ComponentConfigEditor;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.JobController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.platform.ComponentHost;
import art.arcane.react.platform.RegistryHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.project.config.TomlFiles;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Feature, tweak and action ticking on mods. The server thread keeps the clock and React's async pool runs each due
 * tick, as React's ticker does on Bukkit; a tick still running when its next turn comes is skipped.
 */
final class ModComponentHost implements ComponentHost {
    private final ModTicks ticks;
    private final ExecutorService async;
    private final JobController jobs;
    private final BooleanSupplier ready;
    private final Logger logger;
    private final ComponentConfigEditor editor;
    private final Predicate<String> integrationInstalled;
    private final BooleanSupplier integrationSecrets;
    private volatile boolean enabled = true;
    private volatile boolean monitoringOnly;

    ModComponentHost(ModTicks ticks, ExecutorService async, JobController jobs, BooleanSupplier ready, Logger logger,
                     RegistryHost registry, Predicate<String> integrationInstalled, BooleanSupplier integrationSecrets) {
        this.ticks = ticks;
        this.async = async;
        this.jobs = jobs;
        this.ready = ready;
        this.logger = logger;
        this.editor = new ComponentConfigEditor(registry, TomlFiles::save);
        this.integrationInstalled = integrationInstalled;
        this.integrationSecrets = integrationSecrets;
    }

    void disable() {
        enabled = false;
    }

    @Override
    public Subscription schedule(Object owner, String id, long intervalMs, Runnable tick) {
        long interval = Math.max(0L, intervalMs);
        AtomicBoolean inFlight = new AtomicBoolean(false);
        long[] lastRun = {System.currentTimeMillis()};
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AutoCloseable repeating = ticks.every(() -> {
            long now = System.currentTimeMillis();
            if (cancelled.get() || now - lastRun[0] < interval || !inFlight.compareAndSet(false, true)) {
                return;
            }
            lastRun[0] = now;
            try {
                async.execute(() -> {
                    try {
                        if (!cancelled.get()) {
                            tick.run();
                        }
                    } catch (RuntimeException failure) {
                        logger.error("Tick task crashed: {}", id, failure);
                    } finally {
                        inFlight.set(false);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                inFlight.set(false);
            }
        });
        return () -> {
            cancelled.set(true);
            try {
                repeating.close();
            } catch (Exception ignored) {
            }
        };
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isRunning() {
        return enabled && ready.getAsBoolean();
    }

    @Override
    public boolean isMonitoringOnly() {
        return monitoringOnly;
    }

    /** Pauses or resumes every feature and tweak while samplers, maps and commands keep running. Server thread only. */
    void setMonitoringOnly(boolean monitoringOnly) {
        this.monitoringOnly = monitoringOnly;
        FeatureController features = ReactComponents.features();
        if (features != null) {
            features.reconcileRuntimeMode();
        }
        TweakController tweaks = ReactComponents.tweaks();
        if (tweaks != null) {
            tweaks.reconcileRuntimeMode();
        }
    }

    @Override
    public boolean integrationSecretsEnabled() {
        return integrationSecrets.getAsBoolean();
    }

    @Override
    public boolean hasCapability(String capability) {
        return integrationInstalled.test(capability);
    }

    @Override
    public boolean isIntegrationInstalled(String capability) {
        return integrationInstalled.test(capability);
    }

    @Override
    public boolean applyConfigValue(String path, Object value) {
        return editor.apply(path, value);
    }

    @Override
    public double maxComputeTimeMs() {
        return jobs.getMaxComputeTime();
    }
}
