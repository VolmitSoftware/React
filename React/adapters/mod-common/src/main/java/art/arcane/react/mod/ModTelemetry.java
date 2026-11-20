package art.arcane.react.mod;

import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.GCStatsTracker;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.JobController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.core.telemetry.HostTelemetryFeed;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The parts of React's own telemetry that Bukkit runs as controllers: host telemetry refreshed once a second off the
 * server thread, the budgeted job queue behind {@code J.s}, and the metric history that web routes and map graphs read.
 */
final class ModTelemetry {
    private static final long HOST_REFRESH_INTERVAL_MS = 1_000L;
    private final HostTelemetryFeed host;
    private final JobController jobs = new JobController();
    private final HistoryController history = new HistoryController();
    private final Logger logger;
    private final List<String> hostSamplerIds = new ArrayList<>();
    private ScheduledExecutorService hostRefresh;
    private boolean gcTracked;

    ModTelemetry(File dataFolder, Logger logger) {
        this.host = HostTelemetryFeed.forDataFolder(dataFolder.toPath());
        this.logger = logger;
    }

    HostTelemetryFeed host() {
        return host;
    }

    JobController jobs() {
        return jobs;
    }

    HistoryController history() {
        return history;
    }

    /** Server thread, before the samplers start: the job queue must run before anything queues sync work. */
    void startJobs() {
        jobs.start();
    }

    /** Server thread, once every sampler has started. */
    void start(SampleController samples) {
        GCStatsTracker.acquire();
        gcTracked = true;
        for (Sampler sampler : host.samplers()) {
            if (samples.registerSampler(sampler)) {
                hostSamplerIds.add(sampler.getId());
            }
        }
        ScheduledExecutorService refresh = Executors.newSingleThreadScheduledExecutor((Runnable task) -> {
            Thread thread = new Thread(task, "react-host-telemetry");
            thread.setDaemon(true);
            return thread;
        });
        hostRefresh = refresh;
        refresh.scheduleWithFixedDelay(() -> host.refresh(System.currentTimeMillis()), 0L, HOST_REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        history.start();
        history.postStart();
        Graph.bindLatest((String samplerId) -> {
            MetricSnapshotValue value = history.latest().value(samplerId);
            return value == null ? null : new Graph.Latest(value.available(), value.value());
        });
    }

    void stop(SampleController samples) {
        Graph.bindLatest(null);
        try {
            history.stop();
        } catch (RuntimeException failure) {
            logger.warn("React history did not stop cleanly", failure);
        }
        ScheduledExecutorService refresh = hostRefresh;
        hostRefresh = null;
        if (refresh != null) {
            refresh.shutdownNow();
        }
        if (samples != null) {
            for (String id : hostSamplerIds) {
                samples.unregisterSampler(id);
            }
        }
        hostSamplerIds.clear();
        host.reset();
        if (gcTracked) {
            gcTracked = false;
            GCStatsTracker.release();
        }
        jobs.stop();
    }
}
