package art.arcane.react.core.telemetry;

import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.GCStatsTracker;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.JobController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.core.telemetry.runtime.PathfindingRates;
import art.arcane.react.core.telemetry.runtime.RuntimeMetricSampler;
import art.arcane.react.platform.ReactPlatforms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The parts of React's own telemetry that Bukkit runs as controllers: host telemetry refreshed once a second off the
 * server thread, the budgeted job queue behind {@code J.s}, the metric history that web routes and map graphs read, and
 * the pathfinding rates that Bukkit's runtime telemetry controller publishes.
 */
public final class RuntimeTelemetry {
    private static final long HOST_REFRESH_INTERVAL_MS = 1_000L;
    private final HostTelemetryFeed host;
    private final JobController jobs = new JobController();
    private final HistoryController history = new HistoryController();
    private final PathfindingRates pathfinding = new PathfindingRates(true);
    private final List<String> samplerIds = new ArrayList<>();
    private ScheduledExecutorService hostRefresh;
    private boolean gcTracked;

    public RuntimeTelemetry(File dataFolder) {
        this.host = HostTelemetryFeed.forDataFolder(dataFolder.toPath());
    }

    public HostTelemetryFeed host() {
        return host;
    }

    public JobController jobs() {
        return jobs;
    }

    public HistoryController history() {
        return history;
    }

    public PathfindingRates pathfinding() {
        return pathfinding;
    }

    /** Server thread, before the samplers start: the job queue must run before anything queues sync work. */
    public void startJobs() {
        jobs.start();
    }

    /** Server thread, once every sampler has started. */
    public void start(SampleController samples) {
        GCStatsTracker.acquire();
        gcTracked = true;
        for (Sampler sampler : host.samplers()) {
            if (samples.registerSampler(sampler)) {
                samplerIds.add(sampler.getId());
            }
        }
        pathfinding.reset(System.nanoTime());
        for (RuntimeMetricSampler.Definition definition : PathfindingRates.DEFINITIONS) {
            RuntimeMetricSampler sampler = new RuntimeMetricSampler(definition,
                    () -> pathfinding.metric(definition.id(), System.currentTimeMillis()));
            if (samples.registerSampler(sampler)) {
                samplerIds.add(sampler.getId());
            }
        }
        ScheduledExecutorService refresh = Executors.newSingleThreadScheduledExecutor((Runnable task) -> {
            Thread thread = new Thread(task, "react-host-telemetry");
            thread.setDaemon(true);
            return thread;
        });
        hostRefresh = refresh;
        refresh.scheduleWithFixedDelay(() -> {
            long nowMs = System.currentTimeMillis();
            host.refresh(nowMs);
            pathfinding.refresh(nowMs, System.nanoTime());
        }, 0L, HOST_REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        history.start();
        history.postStart();
        Graph.bindLatest((String samplerId) -> {
            MetricSnapshotValue value = history.latest().value(samplerId);
            return value == null ? null : new Graph.Latest(value.available(), value.value());
        });
    }

    public void stop(SampleController samples) {
        Graph.bindLatest(null);
        try {
            history.stop();
        } catch (RuntimeException failure) {
            ReactPlatforms.get().warn("React history did not stop cleanly", failure);
        }
        ScheduledExecutorService refresh = hostRefresh;
        hostRefresh = null;
        if (refresh != null) {
            refresh.shutdownNow();
        }
        if (samples != null) {
            for (String id : samplerIds) {
                samples.unregisterSampler(id);
            }
        }
        samplerIds.clear();
        pathfinding.reset(System.nanoTime());
        host.reset();
        if (gcTracked) {
            gcTracked = false;
            GCStatsTracker.release();
        }
        jobs.stop();
    }
}
