package art.arcane.react.api.web.relay;

import art.arcane.react.React;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Folia-safety note: RelayClient runs entirely on JDK WS/HTTP threads and the
 * ScheduledExecutorService daemon thread. It never accesses world, entity, or
 * chunk state directly. All server work flows through the RelayRequestBridge
 * (typically RelayLoopbackBridge) which makes loopback HTTP requests into the
 * Javalin server; those Javalin route handlers already marshal Bukkit/Folia
 * state via J.sResult / J.runRegionResult. No additional Bukkit scheduler
 * marshalling is needed here.
 */
public final class RelayClient {

    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();

    private final String agentUri;
    private final ReactServerIdentity identity;
    private final RelayRequestBridge bridge;
    private final RelayBackoff backoff;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;

    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final AtomicInteger attempt = new AtomicInteger(0);

    private volatile WebSocket activeSocket;
    private volatile ScheduledFuture<?> pendingReconnect;

    public RelayClient(String relayUrl, ReactServerIdentity identity, RelayRequestBridge bridge, RelayBackoff backoff) {
        this(relayUrl, identity, bridge, backoff, createDefaultScheduler(), true);
    }

    public RelayClient(String relayUrl, ReactServerIdentity identity, RelayRequestBridge bridge, RelayBackoff backoff, ScheduledExecutorService scheduler) {
        this(relayUrl, identity, bridge, backoff, scheduler, false);
    }

    private RelayClient(String relayUrl, ReactServerIdentity identity, RelayRequestBridge bridge, RelayBackoff backoff, ScheduledExecutorService scheduler, boolean ownsScheduler) {
        String trimmed = relayUrl.endsWith("/") ? relayUrl.substring(0, relayUrl.length() - 1) : relayUrl;
        this.agentUri = trimmed + "/agent";
        this.identity = identity;
        this.bridge = bridge;
        this.backoff = backoff;
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
    }

    private static ScheduledExecutorService createDefaultScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "react-relay-client");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        stopped.set(false);
        registered.set(false);
        attempt.set(0);
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

        WebSocket ws = activeSocket;
        activeSocket = null;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "stop");
            } catch (Exception ignored) {
            }
        }

        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
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
            HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(uri, new AgentListener())
                .whenComplete((ws, ex) -> {
                    if (ex != null) {
                        React.warn("RelayClient: connect failed: " + ex.getMessage());
                        scheduleReconnect();
                    }
                });
        } catch (Exception e) {
            React.warn("RelayClient: connect error: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (stopped.get()) {
            return;
        }
        long delayMs = backoff.delayForAttempt(attempt.getAndIncrement());
        pendingReconnect = scheduler.schedule(this::doConnect, delayMs, TimeUnit.MILLISECONDS);
    }

    private final class AgentListener implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            if (stopped.get()) {
                try {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stopped");
                } catch (Exception ignored) {
                }
                return;
            }
            activeSocket = webSocket;
            webSocket.request(Long.MAX_VALUE);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (!last) {
                return null;
            }
            String text = textBuffer.toString();
            textBuffer.setLength(0);
            try {
                RelayFrame frame = RelayFrame.parse(text);
                handleFrame(webSocket, frame);
            } catch (Exception e) {
                React.warn("RelayClient: frame parse error: " + e.getMessage());
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            registered.set(false);
            if (activeSocket == webSocket) {
                activeSocket = null;
            }
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            registered.set(false);
            if (activeSocket == webSocket) {
                activeSocket = null;
            }
            React.warn("RelayClient: WS error: " + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()));
            scheduleReconnect();
        }
    }

    private void handleFrame(WebSocket webSocket, RelayFrame frame) {
        if (frame.type() == null) {
            return;
        }
        switch (frame.type()) {
            case "challenge" -> handleChallenge(webSocket, frame);
            case "registered" -> handleRegistered();
            case "route" -> handleRoute(webSocket, frame);
            case "error" -> React.warn("RelayClient: broker error: " + (frame.payload() != null ? frame.payload().toString() : "(no payload)"));
            default -> React.warn("RelayClient: unknown frame type: " + frame.type());
        }
    }

    private void handleChallenge(WebSocket webSocket, RelayFrame frame) {
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

            RelayFrame registerFrame = new RelayFrame("register", identity.fingerprint(), "hs", registerPayload);
            webSocket.sendText(registerFrame.toJson(), true);
        } catch (Exception e) {
            React.warn("RelayClient: challenge handling error: " + e.getMessage());
        }
    }

    private void handleRegistered() {
        registered.set(true);
        attempt.set(0);
    }

    private void handleRoute(WebSocket webSocket, RelayFrame frame) {
        String requestId = frame.requestId();
        try {
            JsonObject payload = frame.payload();
            if (payload == null) {
                sendErrorData(webSocket, requestId, "route frame missing payload");
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
            dataPayload.add("body", response.body());

            RelayFrame dataFrame = new RelayFrame("data", identity.fingerprint(), requestId, dataPayload);
            webSocket.sendText(dataFrame.toJson(), true);
        } catch (Exception e) {
            React.warn("RelayClient: route dispatch error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            sendErrorData(webSocket, requestId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void sendErrorData(WebSocket webSocket, String requestId, String message) {
        try {
            JsonObject error = new JsonObject();
            error.addProperty("message", message);
            JsonObject errorWrapper = new JsonObject();
            errorWrapper.add("error", error);

            JsonObject dataPayload = new JsonObject();
            dataPayload.addProperty("status", 502);
            dataPayload.add("body", errorWrapper);

            RelayFrame dataFrame = new RelayFrame("data", identity.fingerprint(), requestId, dataPayload);
            webSocket.sendText(dataFrame.toJson(), true);
        } catch (Exception ignored) {
        }
    }
}
