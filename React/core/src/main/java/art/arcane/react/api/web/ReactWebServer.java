package art.arcane.react.api.web;

import art.arcane.react.api.web.pairing.PairingLink;
import art.arcane.react.api.web.pairing.PairingNotices;
import art.arcane.react.api.web.pairing.ServerNames;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.api.web.pairing.WebTokens;
import art.arcane.react.api.web.relay.ReactIdentityStore;
import art.arcane.react.api.web.relay.ReactServerIdentity;
import art.arcane.react.api.web.relay.RelayBackoff;
import art.arcane.react.api.web.relay.RelayClient;
import art.arcane.react.api.web.relay.RelayLoopbackBridge;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.api.web.dto.EnvironmentDto;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.ErrorEnvelope;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.api.web.heatmap.HeatmapSerializer;
import art.arcane.react.api.web.heatmap.HeatmapViewportPlanner;
import art.arcane.react.api.web.heatmap.ReactHeatmapCellProvider;
import art.arcane.react.api.web.network.NetworkHub;
import art.arcane.react.api.web.network.NetworkLink;
import art.arcane.react.api.web.network.NetworkProtocol;
import art.arcane.react.api.web.network.NetworkRegistration;
import art.arcane.react.api.web.network.NetworkRoutes;
import art.arcane.react.api.web.resource.ActionResource;
import art.arcane.react.api.web.resource.CapabilityResource;
import art.arcane.react.api.web.resource.ConfigResource;
import art.arcane.react.api.web.resource.ConsoleResource;
import art.arcane.react.api.web.resource.ControlResource;
import art.arcane.react.api.web.resource.EnvironmentResource;
import art.arcane.react.api.web.resource.HeatmapResource;
import art.arcane.react.api.web.resource.IdentityResource;
import art.arcane.react.api.web.resource.IncidentResource;
import art.arcane.react.api.web.resource.IntegrationResource;
import art.arcane.react.api.web.resource.LogsResource;
import art.arcane.react.api.web.resource.MetricsResource;
import art.arcane.react.api.web.resource.OwnerResource;
import art.arcane.react.api.web.resource.PairResource;
import art.arcane.react.api.web.resource.PlayerResource;
import art.arcane.react.api.web.resource.SummaryResource;
import art.arcane.react.api.web.resource.WhoamiResource;
import art.arcane.react.api.web.resource.WorldResource;
import art.arcane.react.api.web.ws.CoalescingWsChannel;
import art.arcane.react.api.web.ws.JettyWsTransport;
import art.arcane.react.api.web.ws.LogWsChannel;
import art.arcane.react.api.web.ws.WebSocketSessions;
import art.arcane.react.api.web.ws.WsAuthenticationGate;
import art.arcane.react.api.web.ws.WsMetricsBroadcaster;
import art.arcane.react.core.incident.IncidentRecord;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.WebHost;
import art.arcane.react.util.common.scheduling.J;
import com.google.gson.Gson;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.Javalin;
import io.javalin.http.HttpResponseException;
import io.javalin.http.NotFoundResponse;
import io.javalin.util.JavalinBindException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

@Getter
@Setter
public class ReactWebServer {

    private static final int WS_SEND_THREADS = 4;
    private static final int WS_SEND_QUEUE_CAPACITY = 2048;
    private static final int WS_UNAUTHENTICATED_CAPACITY = 2048;
    private static final int PORT_SEARCH_ATTEMPTS = 100;
    private static final long WS_AUTHENTICATION_TIMEOUT_MILLIS = 5000L;
    private static final long WS_AUTHENTICATION_SWEEP_MILLIS = 1000L;
    private static final long WS_PING_INTERVAL_MILLIS = 15_000L;
    private static final long METRICS_POLL_MILLIS = 100L;
    private static final long TOKEN_HOUSEKEEPING_MILLIS = 1000L;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient PairingNotices pairingNotices = new PairingNotices(() -> this.tokens, this::auditLog,
        (issuerId, linkLabel, refusal, origin) -> host().tellPairingIssuer(issuerId, linkLabel, refusal, origin));
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient WebHost host;

    private volatile WebConfiguration config = new WebConfiguration();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient Object hotloadLock = new Object();
    private transient volatile File dataFolder;
    private transient volatile WebTokens tokens;
    private transient volatile Clock clock = Clock.systemUTC();
    private transient volatile ReactServerIdentity identity;
    private transient volatile ControlBackend featureControlBackend;
    private transient volatile ControlBackend tweakControlBackend;
    private transient volatile WorldBackend worldBackend;
    private transient volatile Javalin app;
    private transient volatile int boundPort;
    private transient volatile RelayClient relayClient;
    private transient volatile NetworkLink networkLink;
    private transient volatile NetworkRoutes networkRoutes;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient String networkBearer;
    private transient volatile CountDownLatch startLatch;
    private transient volatile Throwable startFailure;
    private transient volatile WebSocketSessions<CoalescingWsChannel> metricsSessions;
    private transient volatile WsMetricsBroadcaster metricsBroadcaster;
    private transient volatile ScheduledExecutorService wsPushExecutor;
    private transient volatile ExecutorService wsSendExecutor;
    private transient volatile AuditLog auditLog;
    private transient volatile ActionBackend actionBackend;
    private transient volatile ActionDispatcher actionDispatcher;
    private transient volatile ConsoleCommandDispatcher consoleCommandDispatcher;
    private transient volatile PlayerBackend playerBackend;
    private transient volatile Supplier<WebHost.IncidentScore> incidentScoreSupplier;
    private transient volatile Supplier<String> incidentStateSupplier;
    private transient volatile IntFunction<List<IncidentRecord>> incidentRecordsSupplier;
    private transient volatile Supplier<EnvironmentDto> environmentSnapshotSupplier;
    private transient volatile Supplier<ConfigSectionDto[]> configTreeSupplier;
    private transient volatile ConfigApplier configApplier;
    private transient volatile PresetApplier presetApplier;
    private transient volatile RingLogHandler logHandler;
    private transient volatile WebSocketSessions<LogWsChannel> logSessions;
    private transient volatile WsAuthenticationGate wsAuthenticationGate;
    private transient volatile Logger attachedLogger;
    private transient volatile Log4jConsoleCapture log4jConsoleCapture;
    @Setter(AccessLevel.NONE)
    private final transient ActionTicketRegistry actionTickets =
        new ActionTicketRegistry(() -> WebRevisions.bump(WebRevisions.Kind.ACTIONS));
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient AtomicLong logSequence = new AtomicLong();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient long lifecycleGeneration;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient boolean starting;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient boolean stopped;

    /** A server reading everything from {@code host}; subclasses that override {@link #host()} may pass null. */
    public ReactWebServer(WebHost host) {
        this.host = host;
    }

    /** Where this server reads the platform's data. */
    protected WebHost host() {
        return host;
    }

    public int activeWebSocketSessions() {
        WebSocketSessions localMetricsSessions = metricsSessions;
        WebSocketSessions localLogSessions = logSessions;
        int metricCount = localMetricsSessions == null ? 0 : localMetricsSessions.size();
        int logCount = localLogSessions == null ? 0 : localLogSessions.size();
        return metricCount + logCount;
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

    protected int portSearchAttempts() {
        return PORT_SEARCH_ATTEMPTS;
    }

    protected long wsPingIntervalMillis() {
        return WS_PING_INTERVAL_MILLIS;
    }

    protected void reportPortBindFailure(int requestedPort, int lastPort) {
        String ports = requestedPort == lastPort
            ? Integer.toString(requestedPort)
            : requestedPort + "-" + lastPort;
        WebLog.warn("React web listener could not bind to port range " + ports + "; listener remains disabled.");
    }

    protected void beforeListenerBind() {
    }

    public void start() {
        synchronized (this) {
            stopped = false;
        }
        loadConfiguration();
    }

    public void loadConfiguration() {
        WebHost localHost = host();
        if (dataFolder != null || localHost == null) {
            return;
        }
        try {
            Optional<WebConfiguration> loaded = localHost.loadConfiguration();
            if (loaded.isEmpty()) {
                return;
            }
            config = validatedConfiguration(loaded.get());
            startFailure = null;
        } catch (IOException | IllegalArgumentException e) {
            WebConfiguration failedConfiguration = new WebConfiguration();
            failedConfiguration.setListenerEnabled(false);
            config = failedConfiguration;
            startFailure = e;
            WebLog.reportError("Failed to load web.toml: " + e.getMessage(), e);
        }
    }

    public boolean applyHotloadSnapshot(WebConfiguration prepared) {
        WebConfiguration candidate = validatedConfiguration(prepared);
        synchronized (hotloadLock) {
            WebRuntime previousRuntime;
            WebConfiguration previousConfiguration;
            CountDownLatch cancelledLatch;
            CountDownLatch latch = new CountDownLatch(1);
            boolean restoreListener;
            long generation;
            synchronized (this) {
                if (stopped) {
                    return false;
                }
                if (!starting && candidate.equals(config) && (app != null || !candidate.isListenerEnabled())) {
                    return true;
                }
                previousConfiguration = config;
                restoreListener = app != null || (starting && config.isListenerEnabled());
                cancelledLatch = starting ? startLatch : null;
                previousRuntime = detachRuntime();
                generation = ++lifecycleGeneration;
                starting = true;
                startLatch = latch;
                startFailure = null;
            }
            if (cancelledLatch != null) {
                cancelledLatch.countDown();
            }
            closeRuntime(previousRuntime);
            if (!candidate.isListenerEnabled()) {
                synchronized (this) {
                    if (!isCurrentGeneration(generation)) {
                        latch.countDown();
                        return false;
                    }
                    config = candidate;
                    starting = false;
                }
                latch.countDown();
                return true;
            }
            if (runStartupWithPluginClassLoader(generation, latch, candidate)) {
                return true;
            }
            reportHotloadBindFailure();
            CountDownLatch rollbackLatch = new CountDownLatch(1);
            synchronized (this) {
                if (lifecycleGeneration != generation || stopped || !restoreListener) {
                    return false;
                }
                generation = ++lifecycleGeneration;
                starting = true;
                startLatch = rollbackLatch;
                startFailure = null;
            }
            if (!runStartupWithPluginClassLoader(generation, rollbackLatch, previousConfiguration)) {
                reportHotloadBindFailure();
            }
            return false;
        }
    }

    private void reportHotloadBindFailure() {
        Throwable failure = startFailure;
        if (failure instanceof JavalinBindException && isBindFailure(failure)) {
            WebLog.reportError("WebController could not bind while applying web.toml.", failure);
        }
    }

    protected static WebConfiguration validatedConfiguration(WebConfiguration source) {
        Objects.requireNonNull(source, "configuration");
        WebConfiguration snapshot = new WebConfiguration();
        snapshot.setListenerEnabled(source.isListenerEnabled());
        snapshot.setListenAddress(source.getListenAddress() == null ? "" : source.getListenAddress().trim());
        snapshot.setPort(source.getPort());
        snapshot.setAdvertisedUrl(source.getAdvertisedUrl() == null ? "" : source.getAdvertisedUrl().trim());
        snapshot.setAllowedOrigins(WebOrigins.canonical(source.getAllowedOrigins() == null ? List.of() : source.getAllowedOrigins()));
        snapshot.setAppUrl(source.getAppUrl() == null || source.getAppUrl().isBlank() ? WebConfiguration.DEFAULT_APP_URL : source.getAppUrl().trim());
        snapshot.setServerName(source.getServerName() == null ? "" : source.getServerName().trim());
        snapshot.setTokenDays(source.getTokenDays());
        snapshot.setRequireTokenForReads(source.isRequireTokenForReads());
        snapshot.setRelayEnabled(source.isRelayEnabled());
        snapshot.setRelayUrl(source.getRelayUrl() == null ? "" : source.getRelayUrl().trim());
        snapshot.setNetworkProxyUrl(source.getNetworkProxyUrl() == null ? "" : source.getNetworkProxyUrl().trim());
        snapshot.setNetworkSecret(source.getNetworkSecret() == null ? "" : source.getNetworkSecret().trim());
        snapshot.setNetworkServerName(source.getNetworkServerName() == null ? "" : source.getNetworkServerName().trim());
        if (snapshot.getListenAddress().isBlank()) {
            throw new IllegalArgumentException("listenAddress must not be blank");
        }
        if (snapshot.getPort() < 0 || snapshot.getPort() > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (!snapshot.getAdvertisedUrl().isBlank()) {
            validateAdvertisedUrl(snapshot.getAdvertisedUrl());
        }
        validateAppUrl(snapshot.getAppUrl());
        if (snapshot.getServerName().length() > ServerNames.MAXIMUM_LENGTH) {
            throw new IllegalArgumentException("serverName must be at most " + ServerNames.MAXIMUM_LENGTH + " characters");
        }
        if (snapshot.getTokenDays() < 1 || snapshot.getTokenDays() > 365) {
            throw new IllegalArgumentException("tokenDays must be between 1 and 365");
        }
        if (snapshot.isRelayEnabled() && !snapshot.getRelayUrl().isBlank()) {
            URI uri = URI.create(snapshot.getRelayUrl());
            if ((!"ws".equalsIgnoreCase(uri.getScheme()) && !"wss".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
                throw new IllegalArgumentException("relayUrl must be an absolute WS or WSS URL");
            }
        }
        if (!snapshot.getNetworkProxyUrl().isBlank()) {
            URI uri = URI.create(snapshot.getNetworkProxyUrl());
            if ((!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new IllegalArgumentException("networkProxyUrl must be an absolute HTTP or HTTPS URL");
            }
        }
        return snapshot;
    }

    /** Loads the server identity and the token store once, removing the stores of the old pairing code format. */
    public synchronized void loadAuth() {
        File folder = resolveDataFolder();
        if (tokens == null) {
            removeOldPairingFiles(folder);
            try {
                tokens = WebTokens.load(folder, clock);
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

    private static void removeOldPairingFiles(File folder) {
        boolean removed = false;
        for (String name : List.of("web/tokens.toml", "web/secret.key")) {
            File old = new File(folder, name);
            try {
                removed |= Files.deleteIfExists(old.toPath());
            } catch (IOException failure) {
                WebLog.warn("Could not remove " + name + ": " + failure.getMessage());
            }
        }
        if (removed) {
            WebLog.warn("React Web pairings from before pairing links no longer work; pair each browser again with /react web pair");
        }
    }

    private File resolveDataFolder() {
        File folder = dataFolder;
        if (folder == null) {
            ReactPlatform platform = ReactPlatforms.getOrNull();
            folder = platform == null ? null : platform.dataFolder();
        }
        if (folder == null) {
            throw new IllegalStateException("No data folder available for WebController auth");
        }
        return folder;
    }

    protected IdentityDto resolveIdentity() {
        WebHost localHost = host();
        IdentityDto dto = new IdentityDto();
        dto.serverName = serverName();
        dto.folia = localHost != null && localHost.foliaThreading();
        dto.serverId = localHost == null ? "" : localHost.serverAddress();
        ReactPlatform platform = ReactPlatforms.getOrNull();
        dto.platform = platform == null ? "bukkit" : platform.kind().name().toLowerCase(Locale.ROOT);
        dto.loader = platform == null ? "" : platform.platformName();
        dto.ownerNoun = platform == null ? "plugin" : platform.ownerNoun();
        return dto;
    }

    /** The name pairing links, ping, summary and identity give this server. */
    public String serverName() {
        return ServerNames.resolve(config.getServerName(), resolveMotd(), resolveServerFolder());
    }

    protected String resolveMotd() {
        WebHost localHost = host();
        String motd = localHost == null ? null : localHost.motd();
        return motd == null ? "" : motd;
    }

    protected File resolveServerFolder() {
        WebHost localHost = host();
        File folder = localHost == null ? null : localHost.serverFolder();
        return folder == null ? resolveDataFolder() : folder;
    }

    protected String resolveServerVersion() {
        WebHost localHost = host();
        return localHost == null ? "" : localHost.serverVersion();
    }

    protected Logger resolveConsoleLogger() {
        WebHost localHost = host();
        Logger logger = localHost == null ? null : localHost.consoleLogger();
        return logger == null ? Logger.getLogger("") : logger;
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

    private static void validateAppUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (java.net.URISyntaxException invalid) {
            throw new IllegalArgumentException("appUrl must be an absolute HTTP or HTTPS URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) || uri.getHost() == null
            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("appUrl must be an absolute HTTP or HTTPS URL without credentials, query, or fragment");
        }
    }

    private static void validateAdvertisedUrl(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
            || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("advertisedUrl must be an absolute HTTP or HTTPS base URL without credentials, query, or fragment");
        }
    }

    public void postStart() {
        WebConfiguration startupConfiguration = config;
        if (!startupConfiguration.isListenerEnabled()) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        long generation = beginStartup(latch);
        if (generation < 0L) {
            return;
        }
        try {
            executeAsync(() -> runStartupWithPluginClassLoader(generation, latch, startupConfiguration));
        } catch (Throwable failure) {
            recordStartupFailure(generation, failure, startupConfiguration.getPort());
            finishStartup(generation, latch);
        }
    }

    private boolean runStartupWithPluginClassLoader(long generation, CountDownLatch latch, WebConfiguration startupConfiguration) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        ClassLoader pluginClassLoader = ReactWebServer.class.getClassLoader();
        try {
            thread.setContextClassLoader(pluginClassLoader);
            return runStartup(generation, latch, startupConfiguration);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private boolean runStartup(long generation, CountDownLatch latch, WebConfiguration startupConfiguration) {
        WebRuntime runtime = new WebRuntime();
        boolean published = false;
        boolean requireTokenForReads = startupConfiguration.isRequireTokenForReads();
        try {
                if (!isCurrentGeneration(generation)) {
                    return false;
                }
                loadAuth();
                WebHost localHost = host();
                MetricsResource metrics = new MetricsResource(localHost.metrics());
                IdentityResource identity = new IdentityResource(this::resolveIdentity);
                WebAuth auth = new WebAuth(tokens);
                List<String> origins = startupConfiguration.getAllowedOrigins();
                WebOrigins originGate = new WebOrigins(() -> origins, pairingNotices::originRefused);
                String listenAddress = startupConfiguration.getListenAddress();
                int requestedPort = startupConfiguration.getPort();
                int searchAttempts = Math.max(1, portSearchAttempts());
                Javalin javalin = Javalin.create(cfg -> {
                    cfg.jetty.addConnector((server, httpConfiguration) -> new IncrementingPortConnector(
                        server,
                        httpConfiguration,
                        listenAddress,
                        requestedPort,
                        searchAttempts
                    ));
                });
                runtime.app = javalin;
                javalin.exception(HttpResponseException.class, (e, ctx) -> {
                    String msg = e.getMessage() == null ? defaultMessageFor(e.getStatus()) : e.getMessage();
                    ctx.status(e.getStatus()).json(new ErrorEnvelope(new ErrorEnvelope.Message(WebError.codeFor(e.getStatus()), msg)));
                });
                javalin.exception(WebError.class, (e, ctx) -> ctx.status(e.status()).json(e.body()));
                javalin.exception(JsonProcessingException.class, (e, ctx) ->
                    ctx.status(400).json(new ErrorEnvelope(new ErrorEnvelope.Message("bad_request", "Malformed request body"))));
                javalin.exception(Exception.class, (e, ctx) -> {
                    WebLog.warn("Unhandled web exception: " + e.getMessage(), e);
                    ctx.status(500).json(new ErrorEnvelope(new ErrorEnvelope.Message("internal", "Internal server error")));
                });
                javalin.before(originGate);
                CapabilityResource capabilityResource = new CapabilityResource(
                    () -> ReactWebServer.this.identity.fingerprint(),
                    this::serverName,
                    this::isRelayAvailable
                );
                javalin.get("/api/v1/ping", capabilityResource::get);
                PairResource pairResource = new PairResource(
                    tokens,
                    () -> ReactWebServer.this.identity.fingerprint(),
                    this::serverName,
                    pairingNotices
                );
                javalin.post("/api/v1/pair", pairResource::pair);
                if (featureControlBackend == null) {
                    featureControlBackend = localHost.features();
                }
                if (tweakControlBackend == null) {
                    tweakControlBackend = localHost.tweaks();
                }
                if (auditLog == null) {
                    auditLog = new AuditLog(resolveDataFolder());
                }
                WebMutationReporter mutationReporter = localHost.mutationReporter(auditLog);
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
                    javalin.before("/api/v1/metrics/range", auth);
                    javalin.before("/api/v1/metrics/runtime", auth);
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
                javalin.get("/api/v1/metrics/range", metrics::range);
                javalin.get("/api/v1/metrics/runtime", ctx -> ctx.json(new Envelope<>(localHost.runtimeMetrics())));
                javalin.get("/api/v1/features", featureResource::list);
                javalin.get("/api/v1/features/{id}", featureResource::get);
                javalin.put("/api/v1/features/{id}", featureResource::toggle);
                javalin.put("/api/v1/features/{id}/config", featureResource::config);
                javalin.get("/api/v1/tweaks", tweakResource::list);
                javalin.get("/api/v1/tweaks/{id}", tweakResource::get);
                javalin.put("/api/v1/tweaks/{id}", tweakResource::toggle);
                javalin.put("/api/v1/tweaks/{id}/config", tweakResource::config);
                HeatmapSerializer hs = new HeatmapSerializer();
                ReactHeatmapCellProvider provider = new ReactHeatmapCellProvider(
                    localHost::heatmapExporters,
                    localHost.heatmapSampler(),
                    hs,
                    8,
                    HeatmapViewportPlanner.MAX_RADIUS
                );
                HeatmapResource heatmaps = new HeatmapResource(provider);
                if (requireTokenForReads) {
                    javalin.before("/api/v1/heatmaps", auth);
                    javalin.before("/api/v1/heatmaps/{id}", auth);
                }
                javalin.get("/api/v1/heatmaps", heatmaps::list);
                javalin.get("/api/v1/heatmaps/{id}", heatmaps::detail);
                IntegrationResource integrations = new IntegrationResource(
                    localHost::integrations,
                    localHost::integrationTimeline
                );
                if (requireTokenForReads) {
                    javalin.before("/api/v1/integrations", auth);
                }
                javalin.get("/api/v1/integrations", integrations::get);
                OwnerResource owners = new OwnerResource(() -> {
                    ReactPlatform platform = ReactPlatforms.getOrNull();
                    return OwnerListing.dtos(platform == null ? "plugin" : platform.ownerNoun(), localHost.owners());
                });
                if (requireTokenForReads) {
                    javalin.before("/api/v1/owners", auth);
                }
                javalin.get("/api/v1/owners", owners::list);
                PluginApiPackRoutes pluginApiPacks = localHost.pluginApiPacks(mutationReporter);
                if (requireTokenForReads) {
                    javalin.before("/api/v1/plugin-api-packs", auth);
                }
                javalin.before("/api/v1/plugin-api-packs/validate", auth);
                javalin.before("/api/v1/plugin-api-packs/{id}", ctx -> {
                    if (requireTokenForReads || !"GET".equals(ctx.method().name())) {
                        auth.handle(ctx);
                    }
                });
                javalin.get("/api/v1/plugin-api-packs", pluginApiPacks::list);
                javalin.get("/api/v1/plugin-api-packs/{id}", pluginApiPacks::get);
                javalin.post("/api/v1/plugin-api-packs/validate", pluginApiPacks::validate);
                javalin.put("/api/v1/plugin-api-packs/{id}", pluginApiPacks::put);
                javalin.delete("/api/v1/plugin-api-packs/{id}", pluginApiPacks::delete);
                if (worldBackend == null) {
                    worldBackend = localHost.worlds();
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
                    playerBackend = localHost.players();
                }
                PlayerResource playerResource = new PlayerResource(playerBackend, mutationReporter);
                javalin.before("/api/v1/players", auth);
                javalin.before("/api/v1/players/{id}/teleport", auth);
                javalin.get("/api/v1/players", playerResource::list);
                javalin.post("/api/v1/players/{id}/teleport", playerResource::teleport);
                if (actionBackend == null) {
                    actionBackend = localHost.actions();
                }
                if (actionDispatcher == null) {
                    actionDispatcher = localHost.actionDispatcher(actionTickets);
                }
                ActionResource actionResource = new ActionResource(
                    actionBackend,
                    actionDispatcher,
                    mutationReporter,
                    actionTickets
                );
                if (requireTokenForReads) {
                    javalin.before("/api/v1/actions", auth);
                    javalin.before("/api/v1/actions/tickets/{id}", auth);
                }
                javalin.before("/api/v1/actions/{id}/execute", auth);
                javalin.get("/api/v1/actions", actionResource::list);
                javalin.get("/api/v1/actions/tickets/{id}", actionResource::ticket);
                javalin.post("/api/v1/actions/{id}/execute", actionResource::execute);
                if (consoleCommandDispatcher == null) {
                    consoleCommandDispatcher = localHost.console();
                }
                ConsoleResource consoleResource = new ConsoleResource(
                    consoleCommandDispatcher,
                    auditLog,
                    mutationReporter
                );
                javalin.before("/api/v1/console/execute", auth);
                javalin.post("/api/v1/console/execute", consoleResource::execute);
                if (incidentScoreSupplier == null) {
                    incidentScoreSupplier = localHost::incidentScore;
                }
                if (incidentStateSupplier == null) {
                    incidentStateSupplier = localHost::incidentState;
                }
                if (incidentRecordsSupplier == null) {
                    incidentRecordsSupplier = localHost::incidents;
                }
                IncidentResource incidentResource = new IncidentResource(
                    incidentScoreSupplier,
                    incidentStateSupplier,
                    incidentRecordsSupplier
                );
                if (requireTokenForReads) {
                    javalin.before("/api/v1/incidents", auth);
                }
                javalin.get("/api/v1/incidents", incidentResource::get);
                if (environmentSnapshotSupplier == null) {
                    EnvironmentSnapshotProvider environmentProvider = new EnvironmentSnapshotProvider(
                        this::resolveIdentity,
                        this::resolveServerVersion,
                        localHost::hostEnvironment
                    );
                    environmentSnapshotSupplier = environmentProvider::snapshot;
                }
                EnvironmentResource environmentResource = new EnvironmentResource(environmentSnapshotSupplier);
                if (requireTokenForReads) {
                    javalin.before("/api/v1/environment", auth);
                }
                javalin.get("/api/v1/environment", environmentResource::get);
                SummaryResource summaryResource = new SummaryResource(
                    metrics::liveFrame,
                    incidentStateSupplier,
                    incidentRecordsSupplier,
                    this::serverName,
                    () -> ReactWebServer.this.identity.fingerprint()
                );
                javalin.before("/api/v1/summary", auth);
                javalin.get("/api/v1/summary", summaryResource::get);
                if (configTreeSupplier == null) {
                    configTreeSupplier = localHost::configTree;
                }
                if (configApplier == null) {
                    configApplier = localHost::applyConfig;
                }
                if (presetApplier == null) {
                    presetApplier = localHost::applyPreset;
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
                RingLogHandler localLogHandler = new RingLogHandler(RingLogHandler.DEFAULT_CAPACITY, logSequence);
                runtime.logHandler = localLogHandler;
                Logger consoleLogger = resolveConsoleLogger();
                runtime.attachedLogger = consoleLogger;
                consoleLogger.addHandler(localLogHandler);
                try {
                    runtime.log4jConsoleCapture = Log4jConsoleCapture.attach(localLogHandler);
                } catch (NoClassDefFoundError absent) {
                    WebLog.verbose("Log4j is not on this server; React Web's console reads " + consoleLogger.getName());
                } catch (ReflectiveOperationException | SecurityException | LinkageError e) {
                    WebLog.warn("Unable to attach React web console capture to Log4j", e);
                }
                LogsResource logsResource = new LogsResource(localLogHandler::tail);
                javalin.before("/api/v1/logs", auth);
                javalin.get("/api/v1/logs", logsResource::list);
                if (!isCurrentGeneration(generation)) {
                    return false;
                }
                Optional<NetworkHub> network = localHost.network();
                if (network.isPresent()) {
                    WebConfiguration networkConfiguration = startupConfiguration;
                    NetworkRoutes routes = new NetworkRoutes(network.get(), tokens, auth, networkConfiguration::getNetworkSecret,
                        this::serverName, requireTokenForReads, (String origin) -> {
                            boolean allowed = WebOrigins.allows(origins, origin);
                            if (!allowed) {
                                pairingNotices.originRefused(origin);
                            }
                            return allowed;
                        });
                    routes.install(javalin);
                    runtime.networkRoutes = routes;
                }
                WebSocketSessions<CoalescingWsChannel> localMetricsSessions = new WebSocketSessions<>();
                runtime.metricsSessions = localMetricsSessions;
                WebSocketSessions<LogWsChannel> localLogSessions = new WebSocketSessions<>();
                runtime.logSessions = localLogSessions;
                ExecutorService sendExecutor = createWsSendExecutor();
                runtime.wsSendExecutor = sendExecutor;
                WsAuthenticationGate authenticationGate = new WsAuthenticationGate(
                    WS_UNAUTHENTICATED_CAPACITY,
                    WS_AUTHENTICATION_TIMEOUT_MILLIS,
                    this::refuseWsScope
                );
                runtime.wsAuthenticationGate = authenticationGate;
                javalin.ws("/ws/metrics", ws -> {
                    ws.onConnect(ctx -> {
                        if (!WebOrigins.allows(origins, ctx.header("Origin"))) {
                            pairingNotices.originRefused(ctx.header("Origin"));
                            ctx.closeSession(WsAuthenticationGate.CLOSE_ORIGIN_NOT_ALLOWED, "origin_not_allowed");
                            return;
                        }
                        CoalescingWsChannel channel = new CoalescingWsChannel(new JettyWsTransport(ctx));
                        if (!requireTokenForReads) {
                            localMetricsSessions.add(channel);
                            return;
                        }
                        authenticationGate.register(
                            channel,
                            reason -> ctx.closeSession(WsAuthenticationGate.closeCode(reason), reason),
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
                                reason -> ctx.closeSession(WsAuthenticationGate.closeCode(reason), reason)
                            );
                            return;
                        }
                        if (authenticationGate.authenticate(
                            ctx.sessionId(),
                            ctx.message(),
                            localMetricsSessions,
                            reason -> ctx.closeSession(WsAuthenticationGate.closeCode(reason), reason)
                        )) {
                            CoalescingWsChannel admitted = localMetricsSessions.get(ctx.sessionId());
                            WsMetricsBroadcaster current = runtime.metricsBroadcaster;
                            if (admitted != null && current != null) {
                                current.sendCurrent(admitted);
                            }
                        }
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
                    metrics::liveFrame,
                    localMetricsSessions,
                    frame -> gson.toJson(new Envelope<>(frame))
                );
                runtime.metricsBroadcaster = localMetricsBroadcaster;
                javalin.ws("/ws/logs", ws -> {
                    ws.onConnect(ctx -> {
                        if (!WebOrigins.allows(origins, ctx.header("Origin"))) {
                            pairingNotices.originRefused(ctx.header("Origin"));
                            ctx.closeSession(WsAuthenticationGate.CLOSE_ORIGIN_NOT_ALLOWED, "origin_not_allowed");
                            return;
                        }
                        authenticationGate.register(
                            new LogWsChannel(
                                new JettyWsTransport(ctx),
                                sendExecutor,
                                "1".equals(ctx.queryParam("batch")),
                                LogWsChannel.DEFAULT_CAPACITY
                            ),
                            reason -> ctx.closeSession(WsAuthenticationGate.closeCode(reason), reason),
                            "console:read",
                            localLogSessions
                        );
                    });
                    ws.onMessage(ctx -> authenticationGate.authenticate(
                        ctx.sessionId(),
                        ctx.message(),
                        localLogSessions,
                        reason -> ctx.closeSession(WsAuthenticationGate.closeCode(reason), reason)
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
                localLogHandler.setLineListener(line -> {
                    if (!localLogSessions.isEmpty()) {
                        localLogSessions.forEachOpen(channel -> channel.offer(line));
                    }
                });
                ScheduledExecutorService executor = createWsPushExecutor();
                runtime.wsPushExecutor = executor;
                if (!isCurrentGeneration(generation)) {
                    return false;
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
                        WebLog.verbose("WebSocket metrics push failed", t);
                    }
                }, METRICS_POLL_MILLIS, METRICS_POLL_MILLIS, TimeUnit.MILLISECONDS);
                long pingMillis = Math.max(1L, wsPingIntervalMillis());
                executor.scheduleAtFixedRate(() -> {
                    try {
                        localMetricsSessions.forEachOpen(CoalescingWsChannel::ping);
                        localLogSessions.forEachOpen(LogWsChannel::ping);
                        Set<String> inUse = new HashSet<>(localMetricsSessions.tokenIds());
                        inUse.addAll(localLogSessions.tokenIds());
                        inUse.forEach(tokens::touch);
                    } catch (Throwable t) {
                        WebLog.verbose("WebSocket ping failed", t);
                    }
                }, pingMillis, pingMillis, TimeUnit.MILLISECONDS);
                executor.scheduleAtFixedRate(
                    this::expireTokens,
                    TOKEN_HOUSEKEEPING_MILLIS,
                    TOKEN_HOUSEKEEPING_MILLIS,
                    TimeUnit.MILLISECONDS
                );
                if (!isCurrentGeneration(generation)) {
                    return false;
                }
                beforeListenerBind();
                published = bindAndPublishRuntime(generation, runtime, javalin, startupConfiguration);
        } catch (Throwable failure) {
            recordStartupFailure(generation, failure, startupConfiguration.getPort());
        } finally {
            if (!published) {
                closeRuntime(runtime);
            }
            finishStartup(generation, latch);
        }
        return published;
    }

    private synchronized long beginStartup(CountDownLatch latch) {
        if (stopped || starting || app != null) {
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

    private synchronized boolean publishRuntime(long generation, WebRuntime runtime, WebConfiguration startupConfiguration) {
        if (!starting || lifecycleGeneration != generation) {
            return false;
        }
        config = startupConfiguration;
        app = runtime.app;
        boundPort = runtime.boundPort;
        relayClient = runtime.relayClient;
        networkLink = runtime.networkLink;
        networkRoutes = runtime.networkRoutes;
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

    private boolean bindAndPublishRuntime(
        long generation,
        WebRuntime runtime,
        Javalin javalin,
        WebConfiguration startupConfiguration
    ) {
        synchronized (this) {
            if (!starting || lifecycleGeneration != generation) {
                return false;
            }
            javalin.start();
            runtime.boundPort = javalin.port();
            int requestedPort = startupConfiguration.getPort();
            if (requestedPort > 0 && runtime.boundPort != requestedPort) {
                WebLog.warn("React web port " + requestedPort + " was unavailable; listener started on "
                    + runtime.boundPort + ".");
            }
            if (relayConfigured(startupConfiguration) && tokens.wanted()) {
                runtime.relayClient = startRelay(startupConfiguration, runtime.boundPort);
            }
            if (networkConfigured(startupConfiguration)) {
                int webPort = runtime.boundPort;
                NetworkLink link = new NetworkLink(startupConfiguration.getNetworkProxyUrl(), startupConfiguration.getNetworkSecret(),
                    () -> networkRegistration(startupConfiguration, webPort));
                link.start();
                runtime.networkLink = link;
            }
            return publishRuntime(generation, runtime, startupConfiguration);
        }
    }

    private synchronized void recordStartupFailure(long generation, Throwable failure, int requestedPort) {
        if (lifecycleGeneration != generation) {
            return;
        }
        startFailure = failure;
        if (failure instanceof JavalinBindException && isBindFailure(failure)) {
            int lastPort = lastSearchPort(requestedPort, portSearchAttempts());
            reportPortBindFailure(requestedPort, lastPort);
            return;
        }
        WebLog.reportError("WebController failed to start: " + failure.getMessage(), failure);
    }

    private static int lastSearchPort(int requestedPort, int searchAttempts) {
        if (requestedPort < 1 || requestedPort > 65535) {
            return requestedPort;
        }
        long lastPort = (long) requestedPort + Math.max(1, searchAttempts) - 1L;
        return (int) Math.min(65535L, lastPort);
    }

    private static boolean isBindFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof BindException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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

        NetworkLink link = runtime.networkLink;
        runtime.networkLink = null;
        if (link != null) {
            try {
                link.stop();
            } catch (Throwable failure) {
                reportCleanupFailure("network link", failure);
            }
        }

        NetworkRoutes routes = runtime.networkRoutes;
        runtime.networkRoutes = null;
        if (routes != null) {
            try {
                routes.close();
            } catch (Throwable failure) {
                reportCleanupFailure("network sockets", failure);
            }
        }

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
        WebLog.reportError("Failed to close WebController " + resource + ".", failure);
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

    /** Null when the socket's token holds {@code scope}, else the reason its socket closes with. */
    private String refuseWsScope(String token, String scope) {
        if (token == null || token.isBlank()) {
            return "unauthorized";
        }
        try {
            WebToken admitted = tokens.authenticate(token);
            return admitted.hasScope(scope) ? null : "scope_missing";
        } catch (WebError refused) {
            return refused.code();
        }
    }

    /** Ends tokens unused past their lifetime and closes their sockets. Runs every second. */
    private void expireTokens() {
        WebTokens store = tokens;
        if (store == null) {
            return;
        }
        try {
            for (String id : store.expire()) {
                closeSockets(id, "token_expired");
                auditLog().append("server", "expire", "id=" + id, "expired");
            }
        } catch (Throwable failure) {
            WebLog.verbose("React Web token housekeeping failed", failure);
        }
    }

    private void closeSockets(String tokenId, String reason) {
        NetworkRoutes routes = networkRoutes;
        if (routes != null) {
            routes.closeToken(tokenId, reason);
        }
        WebSocketSessions<CoalescingWsChannel> localMetricsSessions = metricsSessions;
        if (localMetricsSessions != null) {
            localMetricsSessions.closeToken(tokenId, WsAuthenticationGate.CLOSE_PAIRING_ENDED, reason);
        }
        WebSocketSessions<LogWsChannel> localLogSessions = logSessions;
        if (localLogSessions != null) {
            localLogSessions.closeToken(tokenId, WsAuthenticationGate.CLOSE_PAIRING_ENDED, reason);
        }
    }

    /** Open sockets of one token, for {@code /react web tokens}. */
    public int socketsOf(String tokenId) {
        WebSocketSessions<CoalescingWsChannel> localMetricsSessions = metricsSessions;
        WebSocketSessions<LogWsChannel> localLogSessions = logSessions;
        return (localMetricsSessions == null ? 0 : localMetricsSessions.count(tokenId))
            + (localLogSessions == null ? 0 : localLogSessions.count(tokenId));
    }

    /**
     * Revokes a token, or {@code all}, and cancels a link not opened yet the same way. The token's sockets close with 4401
     * at once and its next request gets {@code token_revoked}. Returns the ended ids; empty when nothing matched.
     */
    public List<String> revoke(String target) throws IOException {
        loadAuth();
        List<String> ids = tokens.revoke(target);
        for (String id : ids) {
            closeSockets(id, "token_revoked");
        }
        return ids;
    }

    /** A pairing link the command prints, and the link's id and deadline. */
    public record IssuedLink(String url, WebTokens.Link link) {
    }

    /**
     * Makes a one-time link for {@code grant}. Connects the relay the first time someone pairs. Throws
     * {@link IllegalStateException} with the reason while the listener cannot be reached or too many links wait.
     */
    public synchronized IssuedLink createLink(WebTokens.Grant grant) {
        String unavailable = pairingUnavailableReason();
        if (unavailable != null) {
            throw new IllegalStateException(unavailable);
        }
        loadAuth();
        WebConfiguration current = config;
        Optional<String> direct = Optional.of(resolveDirectUrl());
        Optional<String> relay = relayConfigured(current) ? Optional.of(current.getRelayUrl()) : Optional.empty();
        WebTokens.Link link = tokens.pair(grant);
        ensureRelay();
        PairingLink code = new PairingLink(direct, relay, identity.fingerprint(), identity.publicKeyBase64(), link.tokenId(),
            link.secret(), link.expires(), serverName(), grant.scopes(), grant.lifetime().toSeconds(),
            grant.label().isBlank() ? Optional.empty() : Optional.of(grant.label()));
        auditLog().append(grant.issuer(), "pair-issued", "id=" + link.tokenId() + " scopes=" + String.join(",", grant.scopes()), "issued");
        return new IssuedLink(code.url(current.getAppUrl()), link);
    }

    private boolean networkConfigured(WebConfiguration configuration) {
        ReactPlatform platform = ReactPlatforms.getOrNull();
        WebHost localHost = host();
        return !configuration.getNetworkProxyUrl().isBlank() && !configuration.getNetworkSecret().isBlank()
            && (platform == null || platform.kind() != PlatformKind.PROXY) && (localHost == null || localHost.network().isEmpty());
    }

    /** This backend's network membership, empty when web.toml names no proxy or the listener is not running. */
    public Optional<NetworkLink.Status> networkStatus() {
        NetworkLink link = networkLink;
        return link == null ? Optional.empty() : Optional.of(link.status());
    }

    /** On a proxy, its network. */
    public Optional<NetworkHub> networkHub() {
        WebHost localHost = host();
        return localHost == null ? Optional.empty() : localHost.network();
    }

    private NetworkRegistration networkRegistration(WebConfiguration configuration, int webPort) {
        IdentityDto identityDto = resolveIdentity();
        String address = identityDto.serverId == null ? "" : identityDto.serverId;
        int gamePort;
        try {
            gamePort = Integer.parseInt(address.substring(address.lastIndexOf(':') + 1));
        } catch (NumberFormatException unknown) {
            gamePort = 0;
        }
        return new NetworkRegistration(configuration.getNetworkServerName(), serverName(), gamePort, webPort,
            networkBearer(configuration), identity.fingerprint(), identityDto.platform, identityDto.loader, resolveServerVersion(),
            System.currentTimeMillis());
    }

    /**
     * The token the proxy reaches this server with: an admin token issued to the network, which honours the proxy's
     * narrowed scopes. A new one replaces any older network token when this one is gone.
     */
    private synchronized String networkBearer(WebConfiguration configuration) {
        String current = networkBearer;
        if (current != null && tokens.find(current.substring(0, current.indexOf('.'))).isPresent()) {
            return current;
        }
        try {
            for (WebToken token : tokens.list()) {
                if (NetworkProtocol.TOKEN_ISSUER.equals(token.issuer())) {
                    tokens.revoke(token.id());
                }
            }
            WebTokens.Link link = tokens.pair(new WebTokens.Grant(NetworkProtocol.TOKEN_LABEL, WebRole.ADMIN.scopes(),
                Duration.ofDays(configuration.getTokenDays()), NetworkProtocol.TOKEN_ISSUER, Optional.empty()));
            networkBearer = tokens.redeem(link.tokenId(), link.secret(), Optional.of(NetworkProtocol.TOKEN_LABEL)).bearer();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save the network token: " + failure.getMessage(), failure);
        }
        return networkBearer;
    }

    private static boolean relayConfigured(WebConfiguration configuration) {
        return configuration.isRelayEnabled() && configuration.getRelayUrl() != null && !configuration.getRelayUrl().isBlank();
    }

    private RelayClient startRelay(WebConfiguration configuration, int port) {
        RelayClient client = new RelayClient(
            configuration.getRelayUrl(),
            identity,
            createRelayLoopbackBridge(port),
            RelayBackoff.DEFAULT
        );
        client.start();
        return client;
    }

    /** Connects the relay once someone paired or is pairing; a server nobody paired never dials out. */
    public synchronized void ensureRelay() {
        if (app != null && relayClient == null && relayConfigured(config) && tokens != null && tokens.wanted()) {
            relayClient = startRelay(config, boundPort);
        }
    }

    /** Where React Web stands, for {@code /react web status}. */
    public record WebStatus(String problem, String listener, RelayState relay, String relayUrl, int tokens, int links, int sockets,
                            String fingerprint) {
    }

    public enum RelayState { OFF, WAITING, CONNECTING, REGISTERED }

    public synchronized WebStatus status() {
        loadAuth();
        String host = config.getListenAddress();
        String listener = app == null ? "" : (host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host) + ":" + boundPort;
        RelayState relay;
        if (!relayConfigured(config)) {
            relay = RelayState.OFF;
        } else if (relayClient == null) {
            relay = RelayState.WAITING;
        } else {
            relay = relayClient.isRegistered() ? RelayState.REGISTERED : RelayState.CONNECTING;
        }
        return new WebStatus(pairingUnavailableReason(), listener, relay, config.getRelayUrl(), tokens.list().size(),
            tokens.openLinks().size(), activeWebSocketSessions(), identity.fingerprint());
    }

    private AuditLog auditLog() {
        AuditLog current = auditLog;
        if (current == null) {
            current = new AuditLog(resolveDataFolder());
            auditLog = current;
        }
        return current;
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

    public void stop() {
        WebRuntime runtime;
        CountDownLatch cancelledLatch;
        synchronized (this) {
            stopped = true;
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
        runtime.networkLink = networkLink;
        runtime.networkRoutes = networkRoutes;
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
        networkLink = null;
        networkRoutes = null;
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
        private NetworkLink networkLink;
        private NetworkRoutes networkRoutes;
        private WebSocketSessions<CoalescingWsChannel> metricsSessions;
        private WsMetricsBroadcaster metricsBroadcaster;
        private ScheduledExecutorService wsPushExecutor;
        private ExecutorService wsSendExecutor;
        private RingLogHandler logHandler;
        private WebSocketSessions<LogWsChannel> logSessions;
        private WsAuthenticationGate wsAuthenticationGate;
        private Logger attachedLogger;
        private Log4jConsoleCapture log4jConsoleCapture;
    }

    private static final class IncrementingPortConnector extends ServerConnector {
        private final int lastPort;

        private IncrementingPortConnector(
            Server server,
            HttpConfiguration httpConfiguration,
            String host,
            int requestedPort,
            int searchAttempts
        ) {
            super(server, new HttpConnectionFactory(httpConfiguration));
            setHost(host);
            setPort(requestedPort);
            lastPort = lastSearchPort(requestedPort, searchAttempts);
        }

        @Override
        protected ServerSocketChannel openAcceptChannel() throws IOException {
            int requestedPort = getPort();
            if (requestedPort < 1 || requestedPort > 65535) {
                return super.openAcceptChannel();
            }
            int candidatePort = requestedPort;
            while (true) {
                setPort(candidatePort);
                try {
                    return super.openAcceptChannel();
                } catch (IOException failure) {
                    if (!isBindFailure(failure) || candidatePort >= lastPort) {
                        throw failure;
                    }
                    candidatePort++;
                }
            }
        }
    }
}
