package art.arcane.react.core.telemetry;

import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.GCStatsTracker;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.JobController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.core.telemetry.runtime.JfrTelemetryController;
import art.arcane.react.core.telemetry.runtime.PathfindingRates;
import art.arcane.react.core.telemetry.runtime.RuntimeMetricSampler;
import art.arcane.react.platform.PlayerHost;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The parts of React's own telemetry that Bukkit runs as controllers: host telemetry refreshed once a second off the
 * server thread, the budgeted job queue behind {@code J.s}, the metric history that web routes and map graphs read, and
 * the pathfinding rates that Bukkit's runtime telemetry controller publishes. A game server also gets React's
 * self-telemetry, observation rates, runtime diagnostics and JFR samplers through {@link Server}.
 */
public final class RuntimeTelemetry {
    private static final long HOST_REFRESH_INTERVAL_MS = 1_000L;
    private final HostTelemetryFeed host;
    private final JobController jobs = new JobController();
    private final HistoryController history = new HistoryController();
    private final PathfindingRates pathfinding = new PathfindingRates(true);
    private final List<String> samplerIds = new ArrayList<>();
    private final Server server;
    private ScheduledExecutorService hostRefresh;
    private boolean gcTracked;

    /** The game server's own sources for the self-telemetry samplers. */
    public interface ServerSources {
        boolean webAvailable();

        int activeWebSocketSessions();

        /** Null while nothing publishes metrics. */
        PublishedMetricStore publishedStore();

        /** Null while the server-thread task runner is not running. */
        TickerDiagnostics ticker();
    }

    public RuntimeTelemetry(File dataFolder) {
        this.host = HostTelemetryFeed.forDataFolder(dataFolder.toPath());
        this.server = null;
    }

    public RuntimeTelemetry(File dataFolder, ServerSources sources) {
        this.host = HostTelemetryFeed.forDataFolder(dataFolder.toPath());
        this.server = new Server(sources, history);
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

    /** The JFR settings controller of a game server, loaded with the other core controllers; null on a proxy. */
    public JfrTelemetryController jfr() {
        return server == null ? null : server.jfr;
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
        if (server != null) {
            server.start(samples);
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
            if (server != null) {
                server.refresh(samples, nowMs);
            }
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
        if (server != null) {
            server.stop(samples);
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

    private static final class Server {
        private final SelfTelemetry self;
        private final ObservationTelemetry observation = new ObservationTelemetry();
        private final RuntimeDiagnostics diagnostics;
        private final JfrTelemetryController jfr = new JfrTelemetryController();
        private final List<Subscription> players = new ArrayList<>();

        private Server(ServerSources sources, HistoryController history) {
            this.self = new SelfTelemetry(new SelfTelemetry.Sources() {
                @Override
                public HistoryController history() {
                    return history;
                }

                @Override
                public boolean webAvailable() {
                    return sources.webAvailable();
                }

                @Override
                public int activeWebSocketSessions() {
                    return sources.activeWebSocketSessions();
                }

                @Override
                public PublishedMetricStore publishedStore() {
                    return sources.publishedStore();
                }
            });
            this.diagnostics = new RuntimeDiagnostics(sources::ticker, () -> history);
        }

        private void start(SampleController samples) {
            self.start();
            self.registerReactSamplers(samples);
            self.registerPlayerSamplers(samples);
            ReactPlatform platform = ReactPlatforms.getOrNull();
            PlayerHost playerHost = platform == null ? null : platform.playerHost().orElse(null);
            if (playerHost != null) {
                long nowMs = System.currentTimeMillis();
                for (ReactPlayer player : playerHost.players()) {
                    self.playerActive(player.id(), nowMs);
                }
                players.add(playerHost.onJoin((ReactPlayer player) -> self.playerJoined(player.id(), System.currentTimeMillis())));
                players.add(playerHost.onQuit((ReactPlayer player) -> self.playerQuit(player.id(), System.currentTimeMillis())));
            }
            observation.start();
            observation.register(samples);
            diagnostics.start();
            diagnostics.register(samples);
            jfr.start();
            jfr.register(samples);
        }

        private void refresh(SampleController samples, long nowMs) {
            self.refresh(nowMs);
            diagnostics.tick(samples);
            jfr.refresh(nowMs);
        }

        private void stop(SampleController samples) {
            for (Subscription subscription : players) {
                subscription.cancel();
            }
            players.clear();
            jfr.stop();
            jfr.unregister(samples);
            diagnostics.stop(samples);
            observation.stop(samples);
            self.stop(samples);
        }
    }
}
