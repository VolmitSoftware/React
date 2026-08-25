package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.api.web.ActionBackend;
import art.arcane.react.api.web.ActionDispatcher;
import art.arcane.react.api.web.ActionParamCoercer;
import art.arcane.react.api.web.ActionParamSerializer;
import art.arcane.react.api.web.AuditLog;
import art.arcane.react.api.web.BukkitConsoleCommandDispatcher;
import art.arcane.react.api.web.BukkitPlayerBackend;
import art.arcane.react.api.web.BukkitWebMutationReporter;
import art.arcane.react.api.web.ConfigApplier;
import art.arcane.react.api.web.ConfigTreeSerializer;
import art.arcane.react.api.web.ControlBackend;
import art.arcane.react.api.web.ControlMutator;
import art.arcane.react.api.web.ControlSerializer;
import art.arcane.react.api.web.ConsoleCommandDispatcher;
import art.arcane.react.api.web.FeatureWorldBackend;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.PlayerBackend;
import art.arcane.react.api.web.PresetApplier;
import art.arcane.react.api.web.RegistryActionBackend;
import art.arcane.react.api.web.RegistryControlBackend;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebConfigurationSchema;
import art.arcane.react.api.web.WebSecret;
import art.arcane.react.api.web.WebMutationReporter;
import art.arcane.react.api.web.relay.ReactIdentityStore;
import art.arcane.react.api.web.relay.ReactServerIdentity;
import art.arcane.react.api.web.relay.RelayBackoff;
import art.arcane.react.api.web.relay.RelayClient;
import art.arcane.react.api.web.relay.RelayLoopbackBridge;
import art.arcane.react.api.web.WorldBackend;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.ErrorEnvelope;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.api.web.heatmap.BukkitHeatmapChunkSampler;
import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapSerializer;
import art.arcane.react.api.web.heatmap.HeatmapViewportPlanner;
import art.arcane.react.api.web.heatmap.ReactHeatmapCellProvider;
import art.arcane.react.api.web.EnvironmentSnapshotProvider;
import art.arcane.react.api.web.RingLogHandler;
import art.arcane.react.api.web.resource.ActionResource;
import art.arcane.react.api.web.resource.CapabilityResource;
import art.arcane.react.api.web.resource.ConfigResource;
import art.arcane.react.api.web.resource.ConsoleResource;
import art.arcane.react.api.web.resource.ControlResource;
import art.arcane.react.api.web.resource.EnvironmentResource;
import art.arcane.react.api.web.resource.HeatmapResource;
import art.arcane.react.api.web.resource.IdentityResource;
import art.arcane.react.api.web.IncidentTimeline;
import art.arcane.react.api.web.Log4jConsoleCapture;
import art.arcane.react.api.web.resource.IncidentResource;
import art.arcane.react.api.web.resource.IntegrationResource;
import art.arcane.react.api.web.resource.LogsResource;
import art.arcane.react.api.web.resource.MetricsResource;
import art.arcane.react.api.web.resource.PlayerResource;
import art.arcane.react.api.web.resource.WhoamiResource;
import art.arcane.react.api.web.resource.WorldResource;
import art.arcane.react.api.web.ws.LogFrame;
import art.arcane.react.api.web.dto.EnvironmentDto;
import art.arcane.react.content.feature.FeatureIncidentMode;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.ws.CoalescingWsChannel;
import art.arcane.react.api.web.ws.JavalinWsChannel;
import art.arcane.react.api.web.ws.WebSocketSessions;
import art.arcane.react.api.web.ws.WsAuthenticationGate;
import art.arcane.react.api.web.ws.WsMetricsBroadcaster;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.gui.ReactConfigGUI;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigFileSupport;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.HttpResponseException;
import io.javalin.http.NotFoundResponse;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@Getter
@Setter
public class WebController implements IController {

    private static final int WS_SEND_THREADS = 4;
    private static final int WS_SEND_QUEUE_CAPACITY = 2048;
    private static final int WS_UNAUTHENTICATED_CAPACITY = 2048;
    private static final long WS_AUTHENTICATION_TIMEOUT_MILLIS = 5000L;
    private static final long WS_AUTHENTICATION_SWEEP_MILLIS = 1000L;

    private WebConfiguration config = new WebConfiguration();
    private transient volatile File dataFolder;
    private transient volatile byte[] secret;
    private transient volatile TokenStore tokenStore;
    private transient volatile ReactServerIdentity identity;
    private transient volatile SampleController sampleController;
    private transient volatile HistoryController historyController;
    private transient volatile FeatureController featureController;
    private transient volatile TweakController tweakController;
    private transient volatile IntegrationController integrationController;
    private transient volatile ControlBackend featureControlBackend;
    private transient volatile ControlBackend tweakControlBackend;
    private transient volatile WorldBackend worldBackend;
    private transient volatile Javalin app;
    private transient volatile int boundPort;
    private transient volatile RelayClient relayClient;
    private transient volatile CountDownLatch startLatch;
    private transient volatile Throwable startFailure;
    private transient volatile WebSocketSessions metricsSessions;
    private transient volatile WsMetricsBroadcaster metricsBroadcaster;
    private transient volatile ScheduledExecutorService wsPushExecutor;
    private transient volatile ExecutorService wsSendExecutor;
    private transient volatile AuditLog auditLog;
    private transient volatile ActionBackend actionBackend;
    private transient volatile ActionDispatcher actionDispatcher;
    private transient volatile ConsoleCommandDispatcher consoleCommandDispatcher;
    private transient volatile PlayerBackend playerBackend;
    private transient volatile DoubleSupplier incidentScoreSupplier;
    private transient volatile Supplier<String> incidentStateSupplier;
    private transient volatile IntFunction<List<String>> incidentTimelineSupplier;
    private transient volatile Supplier<List<SamplerIncidentScore.Contribution>> incidentContributorsSupplier;
    private transient volatile Supplier<EnvironmentDto> environmentSnapshotSupplier;
    private transient volatile Supplier<ConfigSectionDto[]> configTreeSupplier;
    private transient volatile ConfigApplier configApplier;
    private transient volatile PresetApplier presetApplier;
    private transient volatile RingLogHandler logHandler;
    private transient volatile IntFunction<List<String>> logLinesSupplier;
    private transient volatile WebSocketSessions logSessions;
    private transient volatile WsAuthenticationGate wsAuthenticationGate;
    private transient volatile Logger attachedLogger;
    private transient volatile Log4jConsoleCapture log4jConsoleCapture;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient long lifecycleGeneration;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient boolean starting;

    public WebController() {
    }

    @Override
    public String getId() {
        return "web";
    }

    @Override
    public String getName() {
        return "Web";
    }

    protected void executeAsync(Runnable r) {
        J.a(r);
    }

    protected ExecutorService createWsSendExecutor() {
        AtomicInteger threadSequence = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "react-ws-send-" + threadSequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            WS_SEND_THREADS,
            WS_SEND_THREADS,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(WS_SEND_QUEUE_CAPACITY),
            threadFactory,
            new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    protected ScheduledExecutorService createWsPushExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "react-ws-push");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void start() {
        if (sampleController == null && React.instance != null) {
            sampleController = React.controller(SampleController.class);
        }
        if (historyController == null && React.instance != null) {
            historyController = React.controller(HistoryController.class);
        }
        if (featureController == null && React.instance != null) {
            featureController = React.controller(FeatureController.class);
        }
        if (tweakController == null && React.instance != null) {
            tweakController = React.controller(TweakController.class);
        }
        if (integrationController == null && React.instance != null) {
            integrationController = React.controller(IntegrationController.class);
        }
        loadConfiguration();
    }

    @Override
    public void loadConfiguration() {
        if (dataFolder != null || React.instance == null) {
            return;
        }
        File canonical = React.instance.getDataFile("web.toml");
        File obsoleteJson = React.instance.getDataFile("web.json");
        try {
            WebConfigurationSchema.requireCurrent(canonical, obsoleteJson);
            config = ConfigFileSupport.load(
                canonical,
                null,
                WebConfiguration.class,
                new WebConfiguration(),
                false,
                "web-config",
                "Created missing config [web.toml] from defaults."
            );
            startFailure = null;
        } catch (IOException e) {
            WebConfiguration failedConfiguration = new WebConfiguration();
            failedConfiguration.setListenerEnabled(false);
            config = failedConfiguration;
            startFailure = e;
            React.reportError("Failed to load web.toml: " + e.getMessage(), e);
        }
    }

    public synchronized void loadAuth() {
        File folder = resolveDataFolder();
        if (secret == null) {
            secret = WebSecret.load(folder);
        }
        if (tokenStore == null) {
            try {
                tokenStore = TokenStore.fromToml(tokensFile(folder));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load token store", e);
            }
        }
        if (identity == null) {
            try {
                identity = ReactIdentityStore.loadOrCreate(folder);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load server identity", e);
            }
        }
    }

    public File tokensFile() {
        return tokensFile(resolveDataFolder());
    }

    private static File tokensFile(File folder) {
        return new File(folder, "web/tokens.toml");
    }

    private File resolveDataFolder() {
        File folder = dataFolder;
        if (folder == null && React.instance != null) {
            folder = React.instance.getDataFolder();
        }
        if (folder == null) {
            throw new IllegalStateException("No data folder available for WebController auth");
        }
        return folder;
    }

    protected IdentityDto resolveIdentity() {
        IdentityDto dto = new IdentityDto();
        dto.version = React.instance.getDescription().getVersion();
        dto.serverName = React.instance.getServer().getName();
        dto.folia = J.isFoliaThreading();
        dto.serverId = React.instance.getServer().getIp() + ":" + React.instance.getServer().getPort();
        return dto;
    }

    protected Logger resolveConsoleLogger() {
        if (React.instance != null && React.instance.getServer() != null) {
            return React.instance.getServer().getLogger();
        }
        return Logger.getLogger("");
    }

    protected RelayLoopbackBridge createRelayLoopbackBridge(int port) {
        return new RelayLoopbackBridge(resolveRelayLoopbackUrl(port));
    }

    protected String resolveRelayLoopbackUrl(int port) {
        return "http://127.0.0.1:" + port;
    }

    public String resolveDirectUrl() {
        String unavailableReason = pairingUnavailableReason();
        if (unavailableReason != null) {
            throw new IllegalStateException(unavailableReason);
        }
        String advertisedUrl = config.getAdvertisedUrl();
        if (advertisedUrl != null && !advertisedUrl.isBlank()) {
            String normalized = advertisedUrl.trim();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (!normalized.isBlank()) {
                validateAdvertisedUrl(normalized);
                return normalized;
            }
        }
        String host = config.getListenAddress();
        if (host == null || host.isBlank() || host.equals("0.0.0.0") || host.equals("::") || host.equals("[::]")) {
            host = "127.0.0.1";
        } else if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        return "http://" + host + ":" + boundPort;
    }

    public synchronized String pairingUnavailableReason() {
        if (starting) {
            return "The React web listener is still starting";
        }
        if (startFailure != null) {
            String message = startFailure.getMessage();
            return message == null || message.isBlank()
                ? "The React web listener failed to start"
                : "The React web listener failed to start: " + message;
        }
        if (!config.isListenerEnabled()) {
            return "The React web listener is disabled";
        }
        if (app == null || boundPort < 1) {
            return "The React web listener is not bound";
        }
        return null;
    }

    private static void validateAdvertisedUrl(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
            || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("advertisedUrl must be an absolute HTTP or HTTPS base URL without credentials, query, or fragment");
        }
    }

    @Override
    public void postStart() {
        if (!config.isListenerEnabled()) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        long generation = beginStartup(latch);
        if (generation < 0L) {
            return;
        }
        try {
            loadAuth();
            if (!isCurrentGeneration(generation)) {
                finishStartup(generation, latch);
                return;
            }
            executeAsync(() -> runStartupWithPluginClassLoader(generation, latch));
        } catch (Throwable failure) {
            recordStartupFailure(generation, failure);
            finishStartup(generation, latch);
        }
    }

    private void runStartupWithPluginClassLoader(long generation, CountDownLatch latch) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        ClassLoader pluginClassLoader = WebController.class.getClassLoader();
        try {
            thread.setContextClassLoader(pluginClassLoader);
            runStartup(generation, latch);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private void runStartup(long generation, CountDownLatch latch) {
        WebRuntime runtime = new WebRuntime();
        boolean published = false;
        boolean requireTokenForReads = config.isRequireTokenForReads();
        try {
                MetricsResource metrics = new MetricsResource(
                    sampleController,
                    historyController,
                    new MetricsSerializer()
                );
                IdentityResource identity = new IdentityResource(this::resolveIdentity);
                WebAuth auth = new WebAuth(secret, tokenStore);
                List<String> origins = config.getCorsOrigins();
                Javalin javalin = Javalin.create(cfg -> cfg.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                    if (origins.isEmpty()) {
                        rule.anyHost();
                    } else {
                        for (String origin : origins) {
                            rule.allowHost(origin);
                        }
                    }
                })));
                runtime.app = javalin;
                javalin.exception(HttpResponseException.class, (e, ctx) -> {
                    String msg = e.getMessage() == null ? defaultMessageFor(e.getStatus()) : e.getMessage();
                    ctx.status(e.getStatus()).json(new ErrorEnvelope(new ErrorEnvelope.Message(msg)));
                });
                javalin.exception(Exception.class, (e, ctx) -> {
                    React.warn("Unhandled web exception: " + e.getMessage(), e);
                    ctx.status(500).json(new ErrorEnvelope(new ErrorEnvelope.Message("Internal server error")));
                });
                CapabilityResource capabilityResource = new CapabilityResource(
                    () -> WebController.this.identity.fingerprint(),
                    this::isRelayAvailable
                );
                javalin.get("/api/v1/ping", capabilityResource::get);
                if (featureControlBackend == null) {
                    ControlMutator fcMutator = (path, value) -> J.sResult(() -> ReactConfigGUI.applyAndSave(null, path, value));
                    featureControlBackend = new RegistryControlBackend<Feature>(
                        "feature",
                        () -> {
                            FeatureController fc = featureController;
                            if (fc == null || fc.getFeatures() == null) {
                                return java.util.List.of();
                            }
                            return fc.getFeatures().all();
                        },
                        id -> {
                            FeatureController fc = featureController;
                            if (fc == null || fc.getFeatures() == null) {
                                return null;
                            }
                            return fc.getFeatures().get(id);
                        },
                        Feature::isEnabled,
                        new ControlSerializer(),
                        fcMutator
                    );
                }
                if (tweakControlBackend == null) {
                    ControlMutator tcMutator = (path, value) -> J.sResult(() -> ReactConfigGUI.applyAndSave(null, path, value));
                    tweakControlBackend = new RegistryControlBackend<Tweak>(
                        "tweak",
                        () -> {
                            TweakController tc = tweakController;
                            if (tc == null || tc.getTweaks() == null) {
                                return java.util.List.of();
                            }
                            return tc.getTweaks().all();
                        },
                        id -> {
                            TweakController tc = tweakController;
                            if (tc == null || tc.getTweaks() == null) {
                                return null;
                            }
                            return tc.getTweaks().get(id);
                        },
                        Tweak::isEnabled,
                        new ControlSerializer(),
                        tcMutator
                    );
                }
                if (auditLog == null) {
                    auditLog = new AuditLog(resolveDataFolder());
                }
                WebMutationReporter mutationReporter = new BukkitWebMutationReporter(auditLog);
                ControlResource featureResource = new ControlResource(
                    featureControlBackend,
                    "feature",
                    mutationReporter
                );
                ControlResource tweakResource = new ControlResource(
                    tweakControlBackend,
                    "tweak",
                    mutationReporter
                );
                if (requireTokenForReads) {
                    javalin.before("/api/v1/identity", auth);
                    javalin.before("/api/v1/metrics", auth);
                    javalin.before("/api/v1/metrics/catalog", auth);
                    javalin.before("/api/v1/metrics/history", auth);
                    javalin.before("/api/v1/features", auth);
                    javalin.before("/api/v1/tweaks", auth);
                }
                javalin.before("/api/v1/features/{id}", auth);
                javalin.before("/api/v1/features/{id}/config", auth);
                javalin.before("/api/v1/tweaks/{id}", auth);
                javalin.before("/api/v1/tweaks/{id}/config", auth);
                javalin.get("/api/v1/identity", identity::get);
                javalin.before("/api/v1/whoami", auth);
                javalin.get("/api/v1/whoami", new WhoamiResource()::get);
                javalin.get("/api/v1/metrics", metrics::snapshot);
                javalin.get("/api/v1/metrics/catalog", metrics::historyCatalog);
                javalin.get("/api/v1/metrics/history", metrics::history);
                javalin.get("/api/v1/features", featureResource::list);
                javalin.get("/api/v1/features/{id}", featureResource::get);
                javalin.put("/api/v1/features/{id}", featureResource::toggle);
                javalin.put("/api/v1/features/{id}/config", featureResource::config);
                javalin.get("/api/v1/tweaks", tweakResource::list);
                javalin.get("/api/v1/tweaks/{id}", tweakResource::get);
                javalin.put("/api/v1/tweaks/{id}", tweakResource::toggle);
                javalin.put("/api/v1/tweaks/{id}/config", tweakResource::config);
                HeatmapSerializer hs = new HeatmapSerializer();
                BukkitHeatmapChunkSampler sampler = new BukkitHeatmapChunkSampler();
                ReactHeatmapCellProvider provider = new ReactHeatmapCellProvider(() -> {
                    FeatureController fc = featureController;
                    if (fc == null || fc.getFeatures() == null) {
                        return List.of();
                    }
                    List<ChunkGridExporter> out = new ArrayList<>();
                    for (Feature f : fc.getFeatures().all()) {
                        if (f instanceof ChunkGridExporter cge) {
                            out.add(cge);
                        }
                    }
                    return out;
                }, sampler, hs, 8, HeatmapViewportPlanner.MAX_RADIUS);
                HeatmapResource heatmaps = new HeatmapResource(provider);
                if (requireTokenForReads) {
                    javalin.before("/api/v1/heatmaps", auth);
                    javalin.before("/api/v1/heatmaps/{id}", auth);
                }
                javalin.get("/api/v1/heatmaps", heatmaps::list);
                javalin.get("/api/v1/heatmaps/{id}", heatmaps::detail);
                IntegrationResource integrations = new IntegrationResource(
                    () -> {
                        IntegrationController ic = integrationController != null
                            ? integrationController
                            : (React.instance != null ? React.controller(IntegrationController.class) : null);
                        return ic == null ? java.util.List.of() : ic.statuses();
                    },
                    limit -> {
                        IntegrationController ic = integrationController != null
                            ? integrationController
                            : (React.instance != null ? React.controller(IntegrationController.class) : null);
                        return ic == null ? java.util.List.of() : ic.recentTimeline(limit);
                    }
                );
                if (requireTokenForReads) {
                    javalin.before("/api/v1/integrations", auth);
                }
                javalin.get("/api/v1/integrations", integrations::get);
                if (worldBackend == null) {
                    worldBackend = new FeatureWorldBackend();
                }
                WorldResource worldResource = new WorldResource(worldBackend, mutationReporter);
                if (requireTokenForReads) {
                    javalin.before("/api/v1/worlds", auth);
                }
                javalin.before("/api/v1/worlds/update", auth);
                javalin.before("/api/v1/worlds/{name}", ctx -> {
                    if (!ctx.path().equals("/api/v1/worlds/update")) {
                        auth.handle(ctx);
                    }
                });
                javalin.get("/api/v1/worlds", worldResource::list);
                javalin.put("/api/v1/worlds/update", worldResource::update);
                javalin.put("/api/v1/worlds/{name}", worldResource::updateNamed);
                if (playerBackend == null) {
                    playerBackend = new BukkitPlayerBackend();
                }
                PlayerResource playerResource = new PlayerResource(playerBackend, mutationReporter);
                javalin.before("/api/v1/players", auth);
                javalin.before("/api/v1/players/{id}/teleport", auth);
                javalin.get("/api/v1/players", playerResource::list);
                javalin.post("/api/v1/players/{id}/teleport", playerResource::teleport);
                if (actionBackend == null) {
                    actionBackend = new RegistryActionBackend(
                        () -> {
                            ActionController ac = React.instance != null ? React.controller(ActionController.class) : null;
                            return ac == null || ac.getActions() == null ? java.util.List.of() : ac.getActions().all();
                        },
                        id -> {
                            ActionController ac = React.instance != null ? React.controller(ActionController.class) : null;
                            return ac == null || ac.getActions() == null ? null : ac.getActions().get(id);
                        },
                        new ActionParamSerializer()
                    );
                }
                if (actionDispatcher == null) {
                    actionDispatcher = (id, params) -> {
                        Action<?> action = React.action(id);
                        if (action == null) {
                            throw new NotFoundResponse("Unknown action: " + id);
                        }
                        ActionParams coerced = new ActionParamCoercer().coerce(action, params);
                        String ticketId = UUID.randomUUID().toString();
                        action.createForceful(coerced).queue();
                        return new ActionDispatcher.DispatchResult(ticketId, "queued");
                    };
                }
                ActionResource actionResource = new ActionResource(
                    actionBackend,
                    actionDispatcher,
                    mutationReporter
                );
                if (requireTokenForReads) {
                    javalin.before("/api/v1/actions", auth);
                }
                javalin.before("/api/v1/actions/{id}/execute", auth);
                javalin.get("/api/v1/actions", actionResource::list);
                javalin.post("/api/v1/actions/{id}/execute", actionResource::execute);
                if (consoleCommandDispatcher == null) {
                    consoleCommandDispatcher = new BukkitConsoleCommandDispatcher();
                }
                ConsoleResource consoleResource = new ConsoleResource(
                    consoleCommandDispatcher,
                    auditLog,
                    mutationReporter
                );
                javalin.before("/api/v1/console/execute", auth);
                javalin.post("/api/v1/console/execute", consoleResource::execute);
                if (incidentScoreSupplier == null) {
                    incidentScoreSupplier = () -> {
                        Sampler s = React.instance != null ? React.sampler(SamplerIncidentScore.ID) : null;
                        return s == null ? 0D : s.sample();
                    };
                }
                if (incidentStateSupplier == null) {
                    incidentStateSupplier = () -> {
                        FeatureIncidentMode f = React.instance != null ? React.feature(FeatureIncidentMode.class) : null;
                        if (f == null) {
                            return "UNKNOWN";
                        }
                        if (!f.isEnabled()) {
                            return "DISABLED";
                        }
                        return f.isIncidentActive() ? "ACTIVE" : "NORMAL";
                    };
                }
                if (incidentTimelineSupplier == null) {
                    incidentTimelineSupplier = limit -> IncidentTimeline.global().recent(limit);
                }
                if (incidentContributorsSupplier == null) {
                    incidentContributorsSupplier = () -> {
                        SamplerIncidentScore s = React.instance != null ? React.sampler(SamplerIncidentScore.class) : null;
                        return s == null ? java.util.List.of() : s.contributions();
                    };
                }
                IncidentResource incidentResource = new IncidentResource(
                    incidentScoreSupplier,
                    incidentStateSupplier,
                    incidentTimelineSupplier,
                    incidentContributorsSupplier
                );
                if (requireTokenForReads) {
                    javalin.before("/api/v1/incidents", auth);
                }
                javalin.get("/api/v1/incidents", incidentResource::get);
                if (environmentSnapshotSupplier == null) {
                    EnvironmentSnapshotProvider environmentProvider = new EnvironmentSnapshotProvider(this::resolveIdentity);
                    environmentSnapshotSupplier = environmentProvider::snapshot;
                }
                EnvironmentResource environmentResource = new EnvironmentResource(environmentSnapshotSupplier);
                if (requireTokenForReads) {
                    javalin.before("/api/v1/environment", auth);
                }
                javalin.get("/api/v1/environment", environmentResource::get);
                if (configTreeSupplier == null) {
                    configTreeSupplier = () -> new ConfigTreeSerializer().serialize(ReactConfiguration.get());
                }
                if (configApplier == null) {
                    configApplier = (path, value) -> J.sResult(() -> ReactConfigGUI.applyAndSave(null, path, value));
                }
                if (presetApplier == null) {
                    presetApplier = name -> J.sResult(() -> ReactConfigGUI.applyPresetHeadless(name));
                }
                ConfigResource configResource = new ConfigResource(
                    configTreeSupplier,
                    configApplier,
                    presetApplier,
                    mutationReporter
                );
                if (requireTokenForReads) {
                    javalin.before("/api/v1/config", auth);
                } else {
                    javalin.before("/api/v1/config", ctx -> {
                        if ("PUT".equals(ctx.method().name())) {
                            auth.handle(ctx);
                        }
                    });
                }
                javalin.before("/api/v1/config/preset/{name}", auth);
                javalin.get("/api/v1/config", configResource::get);
                javalin.put("/api/v1/config", configResource::put);
                javalin.post("/api/v1/config/preset/{name}", configResource::preset);
                RingLogHandler localLogHandler = new RingLogHandler();
                runtime.logHandler = localLogHandler;
                IntFunction<List<String>> activeLogLinesSupplier = logLinesSupplier;
                if (activeLogLinesSupplier == null) {
                    activeLogLinesSupplier = localLogHandler::recent;
                }
                Logger consoleLogger = resolveConsoleLogger();
                runtime.attachedLogger = consoleLogger;
                consoleLogger.addHandler(localLogHandler);
                try {
                    runtime.log4jConsoleCapture = Log4jConsoleCapture.attach(localLogHandler);
                } catch (ReflectiveOperationException | SecurityException | LinkageError e) {
                    if (React.instance != null) {
                        React.warn("Unable to attach React web console capture to Log4j", e);
                    }
                }
                LogsResource logsResource = new LogsResource(activeLogLinesSupplier);
                javalin.before("/api/v1/logs", auth);
                javalin.get("/api/v1/logs", logsResource::list);
                if (!isCurrentGeneration(generation)) {
                    return;
                }
                WebSocketSessions localMetricsSessions = new WebSocketSessions();
                runtime.metricsSessions = localMetricsSessions;
                WebSocketSessions localLogSessions = new WebSocketSessions();
                runtime.logSessions = localLogSessions;
                ExecutorService sendExecutor = createWsSendExecutor();
                runtime.wsSendExecutor = sendExecutor;
                WsAuthenticationGate authenticationGate = new WsAuthenticationGate(
                    WS_UNAUTHENTICATED_CAPACITY,
                    WS_AUTHENTICATION_TIMEOUT_MILLIS,
                    this::authorizeWsScope
                );
                runtime.wsAuthenticationGate = authenticationGate;
                javalin.ws("/ws/metrics", ws -> {
                    ws.onConnect(ctx -> {
                        CoalescingWsChannel channel = new CoalescingWsChannel(new JavalinWsChannel(ctx), sendExecutor);
                        if (!requireTokenForReads) {
                            localMetricsSessions.add(channel);
                            return;
                        }
                        authenticationGate.register(
                            channel,
                            reason -> ctx.closeSession(1008, reason),
                            "read",
                            localMetricsSessions
                        );
                    });
                    ws.onMessage(ctx -> {
                        if (!requireTokenForReads) {
                            authenticationGate.authenticateOptional(
                                ctx.sessionId(),
                                ctx.message(),
                                "read",
                                localMetricsSessions,
                                reason -> ctx.closeSession(1008, reason)
                            );
                            return;
                        }
                        authenticationGate.authenticate(
                            ctx.sessionId(),
                            ctx.message(),
                            localMetricsSessions,
                            reason -> ctx.closeSession(1008, reason)
                        );
                    });
                    ws.onBinaryMessage(ctx -> authenticationGate.reject(
                        ctx.sessionId(),
                        localMetricsSessions,
                        reason -> ctx.closeSession(1008, reason),
                        "Authentication frame must be JSON text"
                    ));
                    ws.onClose(ctx -> {
                        authenticationGate.remove(ctx.sessionId());
                        localMetricsSessions.remove(ctx.sessionId());
                    });
                    ws.onError(ctx -> {
                        authenticationGate.remove(ctx.sessionId());
                        localMetricsSessions.remove(ctx.sessionId());
                    });
                });
                Gson gson = new Gson();
                WsMetricsBroadcaster localMetricsBroadcaster = new WsMetricsBroadcaster(
                    metrics::snapshotData,
                    localMetricsSessions,
                    snapshot -> gson.toJson(new Envelope<>(snapshot))
                );
                runtime.metricsBroadcaster = localMetricsBroadcaster;
                javalin.ws("/ws/logs", ws -> {
                    ws.onConnect(ctx -> authenticationGate.register(
                        new CoalescingWsChannel(new JavalinWsChannel(ctx), sendExecutor),
                        reason -> ctx.closeSession(1008, reason),
                        "console:read",
                        localLogSessions
                    ));
                    ws.onMessage(ctx -> authenticationGate.authenticate(
                        ctx.sessionId(),
                        ctx.message(),
                        localLogSessions,
                        reason -> ctx.closeSession(1008, reason)
                    ));
                    ws.onBinaryMessage(ctx -> authenticationGate.reject(
                        ctx.sessionId(),
                        localLogSessions,
                        reason -> ctx.closeSession(1008, reason),
                        "Authentication frame must be JSON text"
                    ));
                    ws.onClose(ctx -> {
                        authenticationGate.remove(ctx.sessionId());
                        localLogSessions.remove(ctx.sessionId());
                    });
                    ws.onError(ctx -> {
                        authenticationGate.remove(ctx.sessionId());
                        localLogSessions.remove(ctx.sessionId());
                    });
                });
                CoalescingWsChannel logBroadcaster = CoalescingWsChannel.broadcastTo(
                    "react-log-broadcast",
                    localLogSessions,
                    sendExecutor
                );
                localLogHandler.setLineListener(line -> {
                    if (!localLogSessions.isEmpty()) {
                        String frame = gson.toJson(new LogFrame("log", line));
                        logBroadcaster.send(frame);
                    }
                });
                long periodMs = Math.max(1L, 1000L / Math.max(1, config.getWsPushHz()));
                ScheduledExecutorService executor = createWsPushExecutor();
                runtime.wsPushExecutor = executor;
                if (!isCurrentGeneration(generation)) {
                    return;
                }
                executor.scheduleAtFixedRate(
                    authenticationGate::expire,
                    WS_AUTHENTICATION_SWEEP_MILLIS,
                    WS_AUTHENTICATION_SWEEP_MILLIS,
                    TimeUnit.MILLISECONDS
                );
                executor.scheduleAtFixedRate(() -> {
                    try {
                        localMetricsBroadcaster.broadcastOnce();
                    } catch (Throwable t) {
                        React.verbose("WebSocket metrics push failed", t);
                    }
                }, periodMs, periodMs, TimeUnit.MILLISECONDS);
                if (!isCurrentGeneration(generation)) {
                    return;
                }
                javalin.start(config.getListenAddress(), config.getPort());
                runtime.boundPort = javalin.port();
                if (!isCurrentGeneration(generation)) {
                    return;
                }
                if (config.isRelayEnabled() && config.getRelayUrl() != null && !config.getRelayUrl().isBlank()) {
                    try {
                        RelayLoopbackBridge bridge = createRelayLoopbackBridge(runtime.boundPort);
                        RelayClient client = new RelayClient(config.getRelayUrl(), WebController.this.identity, bridge, new RelayBackoff(1000L, 30000L));
                        runtime.relayClient = client;
                        client.start();
                    } catch (Throwable t) {
                        React.warn("RelayClient startup failed; relay will be unavailable", t);
                        closeRelayClient(runtime);
                    }
                }
                if (publishRuntime(generation, runtime)) {
                    published = true;
                }
        } catch (Throwable failure) {
            recordStartupFailure(generation, failure);
        } finally {
            if (!published) {
                closeRuntime(runtime);
            }
            finishStartup(generation, latch);
        }
    }

    private synchronized long beginStartup(CountDownLatch latch) {
        if (starting || app != null) {
            return -1L;
        }
        lifecycleGeneration++;
        starting = true;
        startLatch = latch;
        startFailure = null;
        return lifecycleGeneration;
    }

    private synchronized boolean isCurrentGeneration(long generation) {
        return starting && lifecycleGeneration == generation;
    }

    private synchronized boolean publishRuntime(long generation, WebRuntime runtime) {
        if (!starting || lifecycleGeneration != generation) {
            return false;
        }
        app = runtime.app;
        boundPort = runtime.boundPort;
        relayClient = runtime.relayClient;
        metricsSessions = runtime.metricsSessions;
        metricsBroadcaster = runtime.metricsBroadcaster;
        wsPushExecutor = runtime.wsPushExecutor;
        wsSendExecutor = runtime.wsSendExecutor;
        logHandler = runtime.logHandler;
        logSessions = runtime.logSessions;
        wsAuthenticationGate = runtime.wsAuthenticationGate;
        attachedLogger = runtime.attachedLogger;
        log4jConsoleCapture = runtime.log4jConsoleCapture;
        return true;
    }

    private synchronized void recordStartupFailure(long generation, Throwable failure) {
        if (lifecycleGeneration != generation) {
            return;
        }
        startFailure = failure;
        React.reportError("WebController failed to start: " + failure.getMessage(), failure);
    }

    private void finishStartup(long generation, CountDownLatch latch) {
        synchronized (this) {
            if (lifecycleGeneration == generation) {
                starting = false;
            }
        }
        latch.countDown();
    }

    private void closeRuntime(WebRuntime runtime) {
        WsAuthenticationGate authenticationGate = runtime.wsAuthenticationGate;
        if (authenticationGate != null) {
            try {
                authenticationGate.close();
            } catch (Throwable failure) {
                reportCleanupFailure("WebSocket authentication gate", failure);
            }
        }

        RingLogHandler handler = runtime.logHandler;
        if (handler != null) {
            try {
                handler.clearLineListener();
            } catch (Throwable failure) {
                reportCleanupFailure("log listener", failure);
            }
        }

        Log4jConsoleCapture capture = runtime.log4jConsoleCapture;
        if (capture != null) {
            try {
                capture.close();
            } catch (Throwable failure) {
                reportCleanupFailure("Log4j console capture", failure);
            }
        }

        Logger logger = runtime.attachedLogger;
        if (logger != null && handler != null) {
            try {
                logger.removeHandler(handler);
            } catch (Throwable failure) {
                reportCleanupFailure("console log handler", failure);
            }
        }

        if (handler != null) {
            try {
                handler.close();
            } catch (Throwable failure) {
                reportCleanupFailure("ring log handler", failure);
            }
        }

        ScheduledExecutorService pushExecutor = runtime.wsPushExecutor;
        if (pushExecutor != null) {
            try {
                pushExecutor.shutdownNow();
            } catch (Throwable failure) {
                reportCleanupFailure("WebSocket push executor", failure);
            }
        }

        ExecutorService sendExecutor = runtime.wsSendExecutor;
        if (sendExecutor != null) {
            try {
                sendExecutor.shutdownNow();
            } catch (Throwable failure) {
                reportCleanupFailure("WebSocket send executor", failure);
            }
        }

        closeRelayClient(runtime);

        Javalin localApp = runtime.app;
        if (localApp != null) {
            try {
                localApp.stop();
            } catch (Throwable failure) {
                reportCleanupFailure("HTTP server", failure);
            }
        }
    }

    private void closeRelayClient(WebRuntime runtime) {
        RelayClient client = runtime.relayClient;
        runtime.relayClient = null;
        if (client == null) {
            return;
        }
        try {
            client.stop();
        } catch (Throwable failure) {
            reportCleanupFailure("relay client", failure);
        }
    }

    private void reportCleanupFailure(String resource, Throwable failure) {
        React.reportError("Failed to close WebController " + resource + ".", failure);
    }

    private static String defaultMessageFor(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            default -> "Error";
        };
    }

    private boolean authorizeWsScope(String token, String scope) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return PairingToken.verify(secret, token, tokenStore)
            .map(pairingToken -> pairingToken.hasScope(scope))
            .orElse(false);
    }

    private boolean isRelayAvailable() {
        RelayClient client = relayClient;
        return client != null && client.isRegistered();
    }

    public void awaitStart(long timeoutMs) throws InterruptedException {
        CountDownLatch latch = startLatch;
        if (latch != null) {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("WebController did not start within " + timeoutMs + "ms");
            }
        }
        Throwable failure = startFailure;
        if (failure != null) {
            throw new RuntimeException("WebController failed to start", failure);
        }
    }

    @Override
    public void stop() {
        WebRuntime runtime;
        CountDownLatch cancelledLatch;
        synchronized (this) {
            lifecycleGeneration++;
            cancelledLatch = starting ? startLatch : null;
            if (starting) {
                startFailure = new IllegalStateException("WebController stopped during startup");
            }
            starting = false;
            runtime = detachRuntime();
        }
        if (cancelledLatch != null) {
            cancelledLatch.countDown();
        }
        closeRuntime(runtime);
    }

    private WebRuntime detachRuntime() {
        WebRuntime runtime = new WebRuntime();
        runtime.app = app;
        runtime.boundPort = boundPort;
        runtime.relayClient = relayClient;
        runtime.metricsSessions = metricsSessions;
        runtime.metricsBroadcaster = metricsBroadcaster;
        runtime.wsPushExecutor = wsPushExecutor;
        runtime.wsSendExecutor = wsSendExecutor;
        runtime.logHandler = logHandler;
        runtime.logSessions = logSessions;
        runtime.wsAuthenticationGate = wsAuthenticationGate;
        runtime.attachedLogger = attachedLogger;
        runtime.log4jConsoleCapture = log4jConsoleCapture;

        app = null;
        boundPort = 0;
        relayClient = null;
        metricsSessions = null;
        metricsBroadcaster = null;
        wsPushExecutor = null;
        wsSendExecutor = null;
        logHandler = null;
        logSessions = null;
        wsAuthenticationGate = null;
        attachedLogger = null;
        log4jConsoleCapture = null;
        return runtime;
    }

    private static final class WebRuntime {
        private Javalin app;
        private int boundPort;
        private RelayClient relayClient;
        private WebSocketSessions metricsSessions;
        private WsMetricsBroadcaster metricsBroadcaster;
        private ScheduledExecutorService wsPushExecutor;
        private ExecutorService wsSendExecutor;
        private RingLogHandler logHandler;
        private WebSocketSessions logSessions;
        private WsAuthenticationGate wsAuthenticationGate;
        private Logger attachedLogger;
        private Log4jConsoleCapture log4jConsoleCapture;
    }
}
