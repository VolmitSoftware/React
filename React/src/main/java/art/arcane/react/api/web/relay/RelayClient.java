package art.arcane.react.api.web.relay;

import art.arcane.react.React;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Folia-safety note: RelayClient runs entirely on JDK WS/HTTP threads, its dispatch pool and the
 * ScheduledExecutorService daemon thread. It never accesses world, entity, or chunk state directly.
 * All server work flows through the RelayRequestBridge (typically RelayLoopbackBridge) which makes
 * loopback HTTP requests into the Javalin server; those route handlers already marshal Bukkit/Folia
 * state via J.sResult / J.runRegionResult.
 */
public final class RelayClient {

    public static final int DISPATCH_THREADS = 4;
    public static final int DISPATCH_QUEUE_CAPACITY = 256;
    public static final int MAX_INBOUND_CHARS = 2 * 1024 * 1024;
    private static final long DEFAULT_PING_INTERVAL_MILLIS = 20_000L;
    private static final long DEFAULT_IDLE_TIMEOUT_MILLIS = 75_000L;
    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder B64URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    /**
     * A browser sends this header with a random 32-byte value before it trusts the relay with a pairing secret or token;
     * the answer carries {@link #SERVER_KEY_HEADER} and {@link #PROOF_HEADER}, the key's signature over
     * {@link #PROOF_PREFIX} and the challenge, which the browser checks against the key it paired with (the Animus proof).
     */
    public static final String CHALLENGE_HEADER = "X-React-Challenge";
    public static final String SERVER_KEY_HEADER = "X-React-Server-Key";
    public static final String PROOF_HEADER = "X-React-Proof";
    public static final String PROOF_PREFIX = "react-web-proof/1 ";

    private final String agentUri;
    private final ReactServerIdentity identity;
    private final RelayRequestBridge bridge;
    private final RelayBackoff backoff;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final long pingIntervalMillis;
    private final long idleTimeoutMillis;

    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final AtomicInteger attempt = new AtomicInteger(0);
    private final AtomicBoolean unreachableReported = new AtomicBoolean(false);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private volatile AgentConnection active;
    private volatile ScheduledFuture<?> pendingReconnect;
    private volatile ScheduledFuture<?> keepAlive;
    private volatile ThreadPoolExecutor dispatcher;

    public RelayClient(String relayUrl, ReactServerIdentity identity, RelayRequestBridge bridge, RelayBackoff backoff) {
        this(relayUrl, identity, bridge, backoff, createDefaultScheduler(), true,
            DEFAULT_PING_INTERVAL_MILLIS, DEFAULT_IDLE_TIMEOUT_MILLIS);
    }

    public RelayClient(String relayUrl, ReactServerIdentity identity, RelayRequestBridge bridge, RelayBackoff backoff, ScheduledExecutorService scheduler) {
        this(relayUrl, identity, bridge, backoff, scheduler, false, DEFAULT_PING_INTERVAL_MILLIS, DEFAULT_IDLE_TIMEOUT_MILLIS);
    }

    public RelayClient(
        String relayUrl,
        ReactServerIdentity identity,
        RelayRequestBridge bridge,
        RelayBackoff backoff,
        ScheduledExecutorService scheduler,
        long pingIntervalMillis,
        long idleTimeoutMillis
    ) {
        this(relayUrl, identity, bridge, backoff, scheduler, false, pingIntervalMillis, idleTimeoutMillis);
    }

    private RelayClient(
        String relayUrl,
        ReactServerIdentity identity,
        RelayRequestBridge bridge,
        RelayBackoff backoff,
        ScheduledExecutorService scheduler,
        boolean ownsScheduler,
        long pingIntervalMillis,
        long idleTimeoutMillis
    ) {
        String trimmed = relayUrl.endsWith("/") ? relayUrl.substring(0, relayUrl.length() - 1) : relayUrl;
        this.agentUri = trimmed + "/agent";
        this.identity = identity;
        this.bridge = bridge;
        this.backoff = backoff;
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
        this.pingIntervalMillis = Math.max(1L, pingIntervalMillis);
        this.idleTimeoutMillis = Math.max(this.pingIntervalMillis, idleTimeoutMillis);
    }

    private static ScheduledExecutorService createDefaultScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "react-relay-client");
            t.setDaemon(true);
            return t;
        });
    }

    private static ThreadPoolExecutor createDispatcher() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            DISPATCH_THREADS,
            DISPATCH_THREADS,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(DISPATCH_QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "react-relay-dispatch-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public void start() {
        stopped.set(false);
        registered.set(false);
        attempt.set(0);
        dispatcher = createDispatcher();
        long checkMillis = Math.max(1L, Math.min(pingIntervalMillis, idleTimeoutMillis / 3L));
        keepAlive = scheduler.scheduleAtFixedRate(this::keepAlive, checkMillis, checkMillis, TimeUnit.MILLISECONDS);
        doConnect();
    }

    public void stop() {
        stopped.set(true);
        registered.set(false);

        ScheduledFuture<?> pending = pendingReconnect;
        if (pending != null) {
            pending.cancel(false);
            pendingReconnect = null;
        }
        ScheduledFuture<?> ticking = keepAlive;
        if (ticking != null) {
            ticking.cancel(false);
            keepAlive = null;
        }

        AgentConnection connection = active;
        active = null;
        if (connection != null) {
            try {
                connection.socket.sendClose(WebSocket.NORMAL_CLOSURE, "stop");
            } catch (Exception ignored) {
            }
        }

        ThreadPoolExecutor pool = dispatcher;
        if (pool != null) {
            pool.shutdownNow();
        }
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
        http.shutdownNow();
    }

    public boolean isRegistered() {
        return registered.get();
    }

    private void doConnect() {
        if (stopped.get()) {
            return;
        }
        try {
            URI uri = URI.create(agentUri);
            http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new AgentListener())
                .whenComplete((ws, ex) -> {
                    if (ex != null) {
                        unreachable(ex);
                        scheduleReconnect();
                    }
                });
        } catch (Exception e) {
            unreachable(e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (stopped.get()) {
            return;
        }
        long delayMs = backoff.delayMillis(attempt.getAndIncrement(), ThreadLocalRandom.current().nextDouble());
        try {
            pendingReconnect = scheduler.schedule(this::doConnect, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            // scheduler stopped
        }
    }

    /** Says once per outage that the relay cannot be reached; every later try stays quiet until it registers again. */
    private void unreachable(Throwable failure) {
        Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
        if (!stopped.get() && unreachableReported.compareAndSet(false, true)) {
            React.warn("React Web relay unreachable at " + agentUri + ": " + cause.getClass().getSimpleName()
                + "; retrying in the background");
        } else {
            React.verbose("RelayClient: connect failed: " + cause);
        }
    }

    private void keepAlive() {
        AgentConnection connection = active;
        if (connection == null || stopped.get()) {
            return;
        }
        long idleNanos = System.nanoTime() - connection.lastInboundNanos;
        if (idleNanos > TimeUnit.MILLISECONDS.toNanos(idleTimeoutMillis)) {
            React.verbose("RelayClient: no traffic from the relay for " + TimeUnit.NANOSECONDS.toMillis(idleNanos) + " ms; reconnecting");
            connection.socket.abort();
            connectionLost(connection.socket);
            return;
        }
        connection.ping();
    }

    private void connectionLost(WebSocket socket) {
        synchronized (this) {
            AgentConnection connection = active;
            if (connection == null || connection.socket != socket) {
                return;
            }
            active = null;
        }
        registered.set(false);
        scheduleReconnect();
    }

    private final class AgentListener implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder();
        private AgentConnection connection;

        @Override
        public void onOpen(WebSocket webSocket) {
            if (stopped.get()) {
                try {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stopped");
                } catch (Exception ignored) {
                }
                return;
            }
            connection = new AgentConnection(webSocket);
            active = connection;
            webSocket.request(Long.MAX_VALUE);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            touch();
            if (textBuffer.length() + data.length() > MAX_INBOUND_CHARS) {
                textBuffer.setLength(0);
                React.warn("RelayClient: relay frame exceeded " + MAX_INBOUND_CHARS + " characters; reconnecting");
                webSocket.abort();
                connectionLost(webSocket);
                return null;
            }
            textBuffer.append(data);
            if (!last) {
                return null;
            }
            String text = textBuffer.toString();
            textBuffer.setLength(0);
            try {
                RelayFrame frame = RelayFrame.parse(text);
                if (connection != null) {
                    handleFrame(connection, frame);
                }
            } catch (Exception e) {
                React.warn("RelayClient: frame parse error: " + e.getMessage(), e);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            touch();
            return WebSocket.Listener.super.onPing(webSocket, message);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            touch();
            return WebSocket.Listener.super.onPong(webSocket, message);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connectionLost(webSocket);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            React.verbose("RelayClient: WebSocket error", error);
            connectionLost(webSocket);
        }

        private void touch() {
            AgentConnection current = connection;
            if (current != null) {
                current.lastInboundNanos = System.nanoTime();
            }
        }
    }

    private void handleFrame(AgentConnection connection, RelayFrame frame) {
        if (frame.type() == null) {
            return;
        }
        switch (frame.type()) {
            case "challenge" -> handleChallenge(connection, frame);
            case "registered" -> handleRegistered();
            case "route" -> handleRoute(connection, frame);
            case "error" -> React.warn("RelayClient: broker error: " + (frame.payload() != null ? frame.payload().toString() : "(no payload)"));
            default -> React.warn("RelayClient: unknown frame type: " + frame.type());
        }
    }

    private void handleChallenge(AgentConnection connection, RelayFrame frame) {
        try {
            JsonObject payload = frame.payload();
            if (payload == null || !payload.has("nonce")) {
                React.warn("RelayClient: challenge frame missing nonce");
                return;
            }
            byte[] nonce = B64URL_DECODER.decode(payload.get("nonce").getAsString());
            String sig = RelayHandshake.signNonce(identity, nonce);

            JsonObject registerPayload = new JsonObject();
            registerPayload.addProperty("pubKey", identity.publicKeyBase64());
            registerPayload.addProperty("sig", sig);

            connection.send(new RelayFrame("register", identity.fingerprint(), "hs", registerPayload).toJson());
        } catch (Exception e) {
            React.warn("RelayClient: challenge handling error: " + e.getMessage(), e);
        }
    }

    private void handleRegistered() {
        registered.set(true);
        attempt.set(0);
        unreachableReported.set(false);
        React.info("React Web relay registered at " + agentUri + " as " + identity.fingerprint());
    }

    private void handleRoute(AgentConnection connection, RelayFrame frame) {
        ThreadPoolExecutor pool = dispatcher;
        String requestId = frame.requestId();
        try {
            pool.execute(() -> dispatchRoute(connection, frame));
        } catch (RejectedExecutionException busy) {
            sendError(connection, requestId, 503, "React relay agent is busy");
        }
    }

    private void dispatchRoute(AgentConnection connection, RelayFrame frame) {
        String requestId = frame.requestId();
        try {
            JsonObject payload = frame.payload();
            if (payload == null) {
                sendError(connection, requestId, 502, "route frame missing payload");
                return;
            }

            String method = payload.has("method") ? payload.get("method").getAsString() : "GET";
            String path = payload.has("path") ? payload.get("path").getAsString() : "/";
            String body = payload.has("body") ? payload.get("body").getAsString() : "";

            Map<String, String> headers = new HashMap<>();
            if (payload.has("headers") && payload.get("headers").isJsonObject()) {
                JsonObject headersObj = payload.getAsJsonObject("headers");
                for (Map.Entry<String, JsonElement> entry : headersObj.entrySet()) {
                    headers.put(entry.getKey(), entry.getValue().getAsString());
                }
            }

            RelayRequestBridge.RelayResponse response = bridge.dispatch(method, path, headers, body);

            JsonObject dataPayload = new JsonObject();
            dataPayload.addProperty("status", response.status());
            JsonObject proof = proof(headers);
            if (proof != null) {
                dataPayload.add("headers", proof);
            }
            dataPayload.add("body", response.body());
            connection.send(new RelayFrame("data", identity.fingerprint(), requestId, dataPayload).toJson());
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            React.warn("RelayClient: route dispatch error: " + message, e);
            sendError(connection, requestId, 502, message);
        }
    }

    /** The proof headers for a request that carries a well-formed {@link #CHALLENGE_HEADER}, else null. */
    JsonObject proof(Map<String, String> headers) {
        String challenge = null;
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getKey().toLowerCase(Locale.ROOT).equals(CHALLENGE_HEADER.toLowerCase(Locale.ROOT))) {
                challenge = header.getValue();
            }
        }
        if (challenge == null || !challenge.matches("[A-Za-z0-9_-]{43}")) {
            return null;
        }
        JsonObject proof = new JsonObject();
        proof.addProperty(SERVER_KEY_HEADER, identity.publicKeyBase64());
        proof.addProperty(PROOF_HEADER, B64URL_ENCODER.encodeToString(identity.sign((PROOF_PREFIX + challenge).getBytes(StandardCharsets.UTF_8))));
        return proof;
    }

    private void sendError(AgentConnection connection, String requestId, int status, String message) {
        try {
            JsonObject error = new JsonObject();
            error.addProperty("message", message);
            JsonObject errorWrapper = new JsonObject();
            errorWrapper.add("error", error);

            JsonObject dataPayload = new JsonObject();
            dataPayload.addProperty("status", status);
            dataPayload.add("body", errorWrapper);
            connection.send(new RelayFrame("data", identity.fingerprint(), requestId, dataPayload).toJson());
        } catch (Exception ignored) {
        }
    }

    /** One relay socket. The JDK socket allows one outstanding send, so replies from the dispatch pool queue up here. */
    private static final class AgentConnection {
        private final WebSocket socket;
        private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);
        private volatile long lastInboundNanos = System.nanoTime();

        private AgentConnection(WebSocket socket) {
            this.socket = socket;
        }

        private synchronized void send(String text) {
            tail = tail.handle((ignored, failure) -> null).thenCompose(ignored -> socket.sendText(text, true));
        }

        private synchronized void ping() {
            tail = tail.handle((ignored, failure) -> null).thenCompose(ignored -> socket.sendPing(ByteBuffer.allocate(0)));
        }
    }
}
