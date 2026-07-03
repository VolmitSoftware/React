package art.arcane.react.web;

import art.arcane.react.api.web.relay.ReactIdentityStore;
import art.arcane.react.api.web.relay.ReactServerIdentity;
import art.arcane.react.api.web.relay.RelayBackoff;
import art.arcane.react.api.web.relay.RelayClient;
import art.arcane.react.api.web.relay.RelayFrame;
import art.arcane.react.api.web.relay.RelayHandshake;
import art.arcane.react.api.web.relay.RelayRequestBridge;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelayClientIntegrationTest {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();

    private Javalin broker;
    private RelayClient client;
    private ScheduledExecutorService scheduler;

    @AfterEach
    void cleanup() {
        if (client != null) {
            client.stop();
            client = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (broker != null) {
            broker.stop();
            broker = null;
        }
    }

    @Test
    void registersAndAnswersRouteFrame(@TempDir File dataFolder) throws Exception {
        AtomicReference<RelayFrame> dataFrameReceived = new AtomicReference<>();

        AtomicReference<String> invokedMethod = new AtomicReference<>();
        AtomicReference<String> invokedPath = new AtomicReference<>();
        AtomicReference<Map<String, String>> invokedHeaders = new AtomicReference<>();

        ConcurrentHashMap<String, byte[]> sessionNonces = new ConcurrentHashMap<>();

        broker = Javalin.create();
        broker.ws("/agent", ws -> {
            ws.onConnect(ctx -> {
                byte[] nonce = RelayHandshake.newNonce();
                sessionNonces.put(ctx.sessionId(), nonce);
                JsonObject challengePayload = new JsonObject();
                challengePayload.addProperty("nonce", B64URL.encodeToString(nonce));
                ctx.send(new RelayFrame("challenge", null, "hs", challengePayload).toJson());
            });
            ws.onMessage(ctx -> {
                RelayFrame frame = RelayFrame.parse(ctx.message());
                if ("register".equals(frame.type())) {
                    byte[] nonce = sessionNonces.get(ctx.sessionId());
                    byte[] pubKey = ReactServerIdentity.decodePublicKey(frame.payload().get("pubKey").getAsString());
                    byte[] sig = B64URL_DECODER.decode(frame.payload().get("sig").getAsString());

                    boolean valid = ReactServerIdentity.verify(pubKey, sig, nonce);
                    assertTrue(valid, "register signature must verify over the challenge nonce");
                    String expectedServerId = ReactServerIdentity.fingerprint(pubKey);
                    assertEquals(expectedServerId, frame.serverId(), "serverId must match fingerprint(pubKey)");

                    ctx.send(new RelayFrame("registered", null, "hs", new JsonObject()).toJson());

                    JsonObject routePayload = new JsonObject();
                    routePayload.addProperty("method", "GET");
                    routePayload.addProperty("path", "/api/v1/identity");
                    JsonObject headers = new JsonObject();
                    headers.addProperty("Authorization", "Bearer X");
                    routePayload.add("headers", headers);
                    routePayload.addProperty("body", "");
                    ctx.send(new RelayFrame("route", frame.serverId(), "req-1", routePayload).toJson());
                } else if ("data".equals(frame.type())) {
                    dataFrameReceived.set(frame);
                }
            });
        });
        broker.start("127.0.0.1", 0);
        int port = broker.port();

        ReactServerIdentity identity = ReactIdentityStore.loadOrCreate(dataFolder);

        RelayRequestBridge stubBridge = (method, path, headers, body) -> {
            invokedMethod.set(method);
            invokedPath.set(path);
            invokedHeaders.set(headers);
            JsonObject responseBody = JsonParser.parseString("{\"data\":{\"ok\":true}}").getAsJsonObject();
            return new RelayRequestBridge.RelayResponse(200, responseBody);
        };

        scheduler = Executors.newSingleThreadScheduledExecutor();
        client = new RelayClient("ws://127.0.0.1:" + port, identity, stubBridge, new RelayBackoff(100, 1000), scheduler);
        client.start();

        long deadline = System.currentTimeMillis() + 5000;
        while (!client.isRegistered() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(client.isRegistered(), "Client must register within 5s");

        deadline = System.currentTimeMillis() + 5000;
        while (dataFrameReceived.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        RelayFrame dataFrame = dataFrameReceived.get();
        assertNotNull(dataFrame, "Broker must receive a data frame within 5s");
        assertEquals("req-1", dataFrame.requestId(), "data frame requestId must match route requestId");
        assertEquals(200, dataFrame.payload().get("status").getAsInt(), "data frame status must be 200");
        assertTrue(
            dataFrame.payload().getAsJsonObject("body").getAsJsonObject("data").get("ok").getAsBoolean(),
            "data frame body.data.ok must be true"
        );

        assertEquals("GET", invokedMethod.get(), "bridge must be invoked with GET");
        assertEquals("/api/v1/identity", invokedPath.get(), "bridge must be invoked with correct path");
        assertNotNull(invokedHeaders.get(), "bridge must receive headers");
        assertEquals("Bearer X", invokedHeaders.get().get("Authorization"), "bridge must receive Authorization header");
    }

    @Test
    void reconnectsAfterBrokerClose(@TempDir File dataFolder) throws Exception {
        AtomicInteger registerCount = new AtomicInteger(0);

        ConcurrentHashMap<String, byte[]> sessionNonces = new ConcurrentHashMap<>();

        broker = Javalin.create();
        broker.ws("/agent", ws -> {
            ws.onConnect(ctx -> {
                byte[] nonce = RelayHandshake.newNonce();
                sessionNonces.put(ctx.sessionId(), nonce);
                JsonObject challengePayload = new JsonObject();
                challengePayload.addProperty("nonce", B64URL.encodeToString(nonce));
                ctx.send(new RelayFrame("challenge", null, "hs", challengePayload).toJson());
            });
            ws.onMessage(ctx -> {
                RelayFrame frame = RelayFrame.parse(ctx.message());
                if ("register".equals(frame.type())) {
                    byte[] nonce = sessionNonces.get(ctx.sessionId());
                    byte[] pubKey = ReactServerIdentity.decodePublicKey(frame.payload().get("pubKey").getAsString());
                    byte[] sig = B64URL_DECODER.decode(frame.payload().get("sig").getAsString());

                    if (ReactServerIdentity.verify(pubKey, sig, nonce)) {
                        registerCount.incrementAndGet();
                        ctx.send(new RelayFrame("registered", null, "hs", new JsonObject()).toJson());
                        ctx.closeSession(1000, "reconnect test");
                    }
                }
            });
        });
        broker.start("127.0.0.1", 0);
        int port = broker.port();

        ReactServerIdentity identity = ReactIdentityStore.loadOrCreate(dataFolder);
        RelayRequestBridge stubBridge = (method, path, headers, body) ->
            new RelayRequestBridge.RelayResponse(200, new JsonObject());

        scheduler = Executors.newSingleThreadScheduledExecutor();
        client = new RelayClient("ws://127.0.0.1:" + port, identity, stubBridge, new RelayBackoff(20, 100), scheduler);
        client.start();

        long deadline = System.currentTimeMillis() + 10000;
        while (registerCount.get() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(registerCount.get() >= 2, "Broker must observe >= 2 register handshakes; got: " + registerCount.get());
    }
}
