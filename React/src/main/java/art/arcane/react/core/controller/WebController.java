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
import art.arcane.react.api.web.ConfigApplier;
import art.arcane.react.api.web.ConfigTreeSerializer;
import art.arcane.react.api.web.ControlBackend;
import art.arcane.react.api.web.ControlMutator;
import art.arcane.react.api.web.ControlSerializer;
import art.arcane.react.api.web.ConsoleCommandDispatcher;
import art.arcane.react.api.web.FeatureWorldBackend;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.PresetApplier;
import art.arcane.react.api.web.RegistryActionBackend;
import art.arcane.react.api.web.RegistryControlBackend;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebSecret;
import art.arcane.react.api.web.relay.ReactIdentityStore;
import art.arcane.react.api.web.relay.ReactServerIdentity;
import art.arcane.react.api.web.relay.RelayBackoff;
import art.arcane.react.api.web.relay.RelayClient;
import art.arcane.react.api.web.relay.RelayLoopbackBridge;
import art.arcane.react.api.web.WorldBackend;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.api.web.dto.ErrorEnvelope;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.api.web.heatmap.BukkitHeatmapChunkSampler;
import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapSerializer;
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
import art.arcane.react.api.web.ws.WsMetricsBroadcaster;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.gui.ReactConfigGUI;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigFileSupport;
import io.javalin.Javalin;
import io.javalin.http.HttpResponseException;
import io.javalin.http.NotFoundResponse;
import io.javalin.websocket.WsConnectContext;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Getter
@Setter
public class WebController implements IController {

    private WebConfiguration config = new WebConfiguration();
    private transient volatile File dataFolder;
    private transient volatile byte[] secret;
    private transient volatile TokenStore tokenStore;
    private transient volatile ReactServerIdentity identity;
    private transient volatile SampleController sampleController;
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
    private transient volatile Logger attachedLogger;
    private transient volatile Log4jConsoleCapture log4jConsoleCapture;

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

    @Override
    public void start() {
        if (sampleController == null && React.instance != null) {
            sampleController = React.controller(SampleController.class);
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
        File legacy = React.instance.getDataFile("web.json");
        try {
            config = ConfigFileSupport.load(
                canonical,
                legacy,
                WebConfiguration.class,
                new WebConfiguration(),
                true,
                "web-config",
                "Created missing config [web.toml] from defaults."
            );
        } catch (IOException e) {
            React.warn("Failed to load web.toml: " + e.getMessage());
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
        String host = config.getBindAddress();
        if (host == null || host.isBlank() || host.equals("0.0.0.0") || host.equals("::") || host.equals("[::]")) {
            host = "127.0.0.1";
        } else if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        int directPort = boundPort > 0 ? boundPort : config.getPort();
        return "http://" + host + ":" + directPort;
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
        if (!config.isEnabled()) {
            return;
        }
        loadAuth();
        CountDownLatch latch = new CountDownLatch(1);
        startLatch = latch;
        startFailure = null;
        executeAsync(() -> {
            try {
                MetricsResource metrics = new MetricsResource(sampleController, new MetricsSerializer());
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
                javalin.exception(HttpResponseException.class, (e, ctx) -> {
                    String msg = e.getMessage() == null ? defaultMessageFor(e.getStatus()) : e.getMessage();
                    ctx.status(e.getStatus()).json(new ErrorEnvelope(new ErrorEnvelope.Message(msg)));
                });
                javalin.exception(Exception.class, (e, ctx) -> {
                    React.warn("Unhandled web exception: " + e.getMessage());
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
                ControlResource featureResource = new ControlResource(featureControlBackend);
                ControlResource tweakResource = new ControlResource(tweakControlBackend);
                if (config.isRequireTokenForReads()) {
                    javalin.before("/api/v1/identity", auth);
                    javalin.before("/api/v1/metrics", auth);
                    javalin.before("/api/v1/metrics/{id}/history", auth);
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
                javalin.get("/api/v1/metrics/{id}/history", metrics::history);
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
                }, sampler, hs, 8, 16);
                HeatmapResource heatmaps = new HeatmapResource(provider);
                if (config.isRequireTokenForReads()) {
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
                if (config.isRequireTokenForReads()) {
                    javalin.before("/api/v1/integrations", auth);
                }
                javalin.get("/api/v1/integrations", integrations::get);
                if (worldBackend == null) {
                    worldBackend = new FeatureWorldBackend();
                }
                WorldResource worldResource = new WorldResource(worldBackend);
                if (config.isRequireTokenForReads()) {
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
                if (auditLog == null) {
                    auditLog = new AuditLog(resolveDataFolder());
                }
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
                ActionResource actionResource = new ActionResource(actionBackend, actionDispatcher, auditLog);
                if (config.isRequireTokenForReads()) {
                    javalin.before("/api/v1/actions", auth);
                }
                javalin.before("/api/v1/actions/{id}/execute", auth);
                javalin.get("/api/v1/actions", actionResource::list);
                javalin.post("/api/v1/actions/{id}/execute", actionResource::execute);
                if (consoleCommandDispatcher == null) {
                    consoleCommandDispatcher = new BukkitConsoleCommandDispatcher();
                }
                ConsoleResource consoleResource = new ConsoleResource(consoleCommandDispatcher, auditLog);
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
                if (config.isRequireTokenForReads()) {
                    javalin.before("/api/v1/incidents", auth);
                }
                javalin.get("/api/v1/incidents", incidentResource::get);
                if (environmentSnapshotSupplier == null) {
                    environmentSnapshotSupplier = () -> new EnvironmentSnapshotProvider(this::resolveIdentity).snapshot();
                }
                EnvironmentResource environmentResource = new EnvironmentResource(environmentSnapshotSupplier);
                if (config.isRequireTokenForReads()) {
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
                ConfigResource configResource = new ConfigResource(configTreeSupplier, configApplier, presetApplier);
                if (config.isRequireTokenForReads()) {
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
                if (logHandler == null) {
                    logHandler = new RingLogHandler();
                }
                if (logLinesSupplier == null) {
                    RingLogHandler capturedHandler = logHandler;
                    logLinesSupplier = n -> capturedHandler.recent(n);
                }
                Logger consoleLogger = resolveConsoleLogger();
                consoleLogger.addHandler(logHandler);
                attachedLogger = consoleLogger;
                try {
                    log4jConsoleCapture = Log4jConsoleCapture.attach(logHandler);
                } catch (ReflectiveOperationException | SecurityException | LinkageError e) {
                    if (React.instance != null) {
                        consoleLogger.log(Level.WARNING, "Unable to attach React web console capture to Log4j", e);
                    }
                }
                LogsResource logsResource = new LogsResource(logLinesSupplier);
                javalin.before("/api/v1/logs", auth);
                javalin.get("/api/v1/logs", logsResource::list);
                javalin.start(config.getBindAddress(), config.getPort());
                app = javalin;
                boundPort = javalin.port();
                metricsSessions = new WebSocketSessions();
                ExecutorService sendExecutor = Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "react-ws-send");
                    t.setDaemon(true);
                    return t;
                });
                wsSendExecutor = sendExecutor;
                javalin.ws("/ws/metrics", ws -> {
                    ws.onConnect(ctx -> {
                        if (!authorizeWsRead(ctx)) {
                            ctx.closeSession(1008, "Unauthorized");
                            return;
                        }
                        metricsSessions.add(new CoalescingWsChannel(new JavalinWsChannel(ctx), sendExecutor));
                    });
                    ws.onClose(ctx -> metricsSessions.remove(ctx.sessionId()));
                    ws.onError(ctx -> metricsSessions.remove(ctx.sessionId()));
                });
                metricsBroadcaster = new WsMetricsBroadcaster(
                    metrics::snapshotData,
                    metricsSessions,
                    arr -> new com.google.gson.Gson().toJson(new MetricsResource.SnapshotResponse(arr))
                );
                logSessions = new WebSocketSessions();
                WebSocketSessions capturedLogSessions = logSessions;
                ExecutorService capturedSendExecutor = sendExecutor;
                javalin.ws("/ws/logs", ws -> {
                    ws.onConnect(ctx -> {
                        if (!authorizeWsConsoleRead(ctx)) {
                            ctx.closeSession(1008, "Unauthorized");
                            return;
                        }
                        capturedLogSessions.add(new JavalinWsChannel(ctx));
                    });
                    ws.onClose(ctx -> capturedLogSessions.remove(ctx.sessionId()));
                    ws.onError(ctx -> capturedLogSessions.remove(ctx.sessionId()));
                });
                logHandler.setLineListener(line -> {
                    WebSocketSessions sessions = logSessions;
                    if (sessions != null && !sessions.isEmpty()) {
                        String frame = new com.google.gson.Gson().toJson(new LogFrame("log", line));
                        capturedSendExecutor.execute(() -> sessions.broadcast(frame));
                    }
                });
                long periodMs = Math.max(1L, 1000L / Math.max(1, config.getWsPushHz()));
                ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "react-ws-push");
                    t.setDaemon(true);
                    return t;
                });
                wsPushExecutor = executor;
                executor.scheduleAtFixedRate(() -> {
                    try {
                        metricsBroadcaster.broadcastOnce();
                    } catch (Throwable t) {
                        React.warn("ws metrics push failed: " + t.getMessage());
                    }
                }, periodMs, periodMs, TimeUnit.MILLISECONDS);
                if (config.isRelayEnabled() && config.getRelayUrl() != null && !config.getRelayUrl().isBlank()) {
                    try {
                        RelayLoopbackBridge bridge = createRelayLoopbackBridge(boundPort);
                        RelayClient client = new RelayClient(config.getRelayUrl(), WebController.this.identity, bridge, new RelayBackoff(1000L, 30000L));
                        client.start();
                        relayClient = client;
                    } catch (Throwable t) {
                        React.warn("RelayClient startup failed (relay will be unavailable): " + t.getMessage());
                    }
                }
            } catch (Throwable t) {
                startFailure = t;
                React.warn("WebController failed to start: " + t.getMessage());
            }
            latch.countDown();
        });
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

    private boolean authorizeWsRead(WsConnectContext ctx) {
        if (!config.isRequireTokenForReads()) {
            return true;
        }
        return authorizeWsScope(ctx, "read");
    }

    private boolean authorizeWsConsoleRead(WsConnectContext ctx) {
        return authorizeWsScope(ctx, "console:read");
    }

    private boolean authorizeWsScope(WsConnectContext ctx, String scope) {
        String token = ctx.queryParam("token");
        if (token == null) {
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
        RingLogHandler handler = logHandler;
        if (handler != null) {
            handler.clearLineListener();
            Log4jConsoleCapture capture = log4jConsoleCapture;
            if (capture != null) {
                try {
                    capture.close();
                } catch (ReflectiveOperationException e) {
                    Logger logger = attachedLogger;
                    if (logger != null) {
                        logger.log(Level.WARNING, "Unable to detach React web console capture from Log4j", e);
                    }
                }
                log4jConsoleCapture = null;
            }
            Logger logger = attachedLogger;
            if (logger != null) {
                logger.removeHandler(handler);
                attachedLogger = null;
            }
            handler.close();
            logHandler = null;
        }
        logSessions = null;
        ScheduledExecutorService executor = wsPushExecutor;
        if (executor != null) {
            executor.shutdownNow();
            wsPushExecutor = null;
        }
        ExecutorService sendExecutor = wsSendExecutor;
        if (sendExecutor != null) {
            sendExecutor.shutdownNow();
            wsSendExecutor = null;
        }
        metricsSessions = null;
        RelayClient rc = relayClient;
        if (rc != null) {
            rc.stop();
            relayClient = null;
        }
        Javalin localApp = app;
        if (localApp != null) {
            localApp.stop();
            app = null;
        }
    }
}
