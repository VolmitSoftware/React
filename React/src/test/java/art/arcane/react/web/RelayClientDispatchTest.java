package art.arcane.react.web;

import art.arcane.react.api.web.relay.ReactIdentityStore;
import art.arcane.react.api.web.relay.ReactServerIdentity;
import art.arcane.react.api.web.relay.RelayBackoff;
import art.arcane.react.api.web.relay.RelayClient;
import art.arcane.react.api.web.relay.RelayFrame;
import art.arcane.react.api.web.relay.RelayHandshake;
import art.arcane.react.api.web.relay.RelayRequestBridge;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayClientDispatchTest {
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private Javalin broker;
    private RelayClient client;
    private ScheduledExecutorService scheduler;
    private final List<RelayFrame> replies = new CopyOnWriteArrayList<>();
    private final AtomicReference<WsContext> agent = new AtomicReference<>();
    private final AtomicInteger registrations = new AtomicInteger();

    @AfterEach
    void cleanup() {
        if (client != null) {
            client.stop();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (broker != null) {
            broker.stop();
        }
    }

    @Test
    void aSlowRouteDoesNotHoldBackTheOthers(@TempDir File dataFolder) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        startClient(dataFolder, (method, path, headers, body) -> {
            if (path.equals("/api/v1/slow")) {
                await(release);
            }
            return new RelayRequestBridge.RelayResponse(200, new JsonObject());
        }, 20_000L, 75_000L);

        route("slow", "/api/v1/slow");
        for (int index = 0; index < 10; index++) {
            route("fast-" + index, "/api/v1/fast");
        }

        awaitReplies(10);
        assertTrue(replies.stream().noneMatch(frame -> frame.requestId().equals("slow")), "the slow route is still running");
        release.countDown();
        awaitReplies(11);
        assertEquals("slow", replies.get(10).requestId());
    }

    @Test
    void aFullDispatchQueueAnswersBusyInsteadOfStalling(@TempDir File dataFolder) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        startClient(dataFolder, (method, path, headers, body) -> {
            await(release);
            return new RelayRequestBridge.RelayResponse(200, new JsonObject());
        }, 20_000L, 75_000L);

        int capacity = RelayClient.DISPATCH_THREADS + RelayClient.DISPATCH_QUEUE_CAPACITY;
        for (int index = 0; index < capacity + 5; index++) {
            route("r-" + index, "/api/v1/held");
        }

        awaitReplies(5);
        for (RelayFrame reply : replies) {
            assertEquals(503, reply.payload().get("status").getAsInt());
        }
        release.countDown();
        awaitReplies(capacity + 5);
        long ok = replies.stream().filter(frame -> frame.payload().get("status").getAsInt() == 200).count();
        assertEquals(capacity, ok, "every accepted route still gets its answer");
    }

    @Test
    void anOversizedRelayFrameDropsTheConnectionAndReconnects(@TempDir File dataFolder) throws Exception {
        startClient(dataFolder, (method, path, headers, body) -> new RelayRequestBridge.RelayResponse(200, new JsonObject()),
            20_000L, 75_000L);
        assertEquals(1, registrations.get());

        JsonObject payload = new JsonObject();
        payload.addProperty("padding", "x".repeat(RelayClient.MAX_INBOUND_CHARS));
        agent.get().send(new RelayFrame("error", null, null, payload).toJson());

        awaitRegistrations(2);
    }

    @Test
    void aSilentRelayIsDroppedAndReconnected(@TempDir File dataFolder) throws Exception {
        ReactServerIdentity identity = ReactIdentityStore.loadOrCreate(dataFolder);
        AtomicInteger connections = new AtomicInteger();
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread acceptor = Thread.ofVirtual().start(() -> acceptAndStaySilent(silent, connections));
            scheduler = Executors.newSingleThreadScheduledExecutor();
            client = new RelayClient("ws://127.0.0.1:" + silent.getLocalPort(), identity,
                (method, path, headers, body) -> new RelayRequestBridge.RelayResponse(200, new JsonObject()),
                new RelayBackoff(20, 0, 20, 100), scheduler, 100L, 400L);
            client.start();

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (connections.get() < 2 && System.nanoTime() < deadline) {
                Thread.sleep(20L);
            }
            assertTrue(connections.get() >= 2, "a relay that never answers pings must be replaced; connections=" + connections.get());
            client.stop();
            client = null;
            acceptor.interrupt();
        }
    }

    private void startClient(File dataFolder, RelayRequestBridge bridge, long pingMillis, long idleMillis) throws Exception {
        ConcurrentHashMap<String, byte[]> nonces = new ConcurrentHashMap<>();
        broker = Javalin.create();
        broker.ws("/agent", ws -> {
            ws.onConnect(ctx -> {
                byte[] nonce = RelayHandshake.newNonce();
                nonces.put(ctx.sessionId(), nonce);
                JsonObject challenge = new JsonObject();
                challenge.addProperty("nonce", B64URL.encodeToString(nonce));
                ctx.send(new RelayFrame("challenge", null, "hs", challenge).toJson());
            });
            ws.onMessage(ctx -> {
                RelayFrame frame = RelayFrame.parse(ctx.message());
                if ("register".equals(frame.type())) {
                    agent.set(ctx);
                    ctx.send(new RelayFrame("registered", null, "hs", new JsonObject()).toJson());
                    registrations.incrementAndGet();
                } else if ("data".equals(frame.type())) {
                    replies.add(frame);
                }
            });
        });
        broker.start("127.0.0.1", 0);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        client = new RelayClient("ws://127.0.0.1:" + broker.port(), ReactIdentityStore.loadOrCreate(dataFolder), bridge,
            new RelayBackoff(20, 0, 20, 100), scheduler, pingMillis, idleMillis);
        client.start();
        awaitRegistrations(1);
    }

    private void route(String requestId, String path) {
        JsonObject payload = new JsonObject();
        payload.addProperty("method", "GET");
        payload.addProperty("path", path);
        payload.add("headers", new JsonObject());
        agent.get().send(new RelayFrame("route", "server", requestId, payload).toJson());
    }

    private void awaitReplies(int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (replies.size() < count) {
            assertTrue(System.nanoTime() < deadline, "expected " + count + " replies, got " + replies.size());
            Thread.sleep(10L);
        }
    }

    private void awaitRegistrations(int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (registrations.get() < count || !client.isRegistered()) {
            assertTrue(System.nanoTime() < deadline, "expected " + count + " registrations, got " + registrations.get());
            Thread.sleep(10L);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }

    // Completes the WebSocket upgrade, then never sends a frame or answers a ping.
    private static void acceptAndStaySilent(ServerSocket server, AtomicInteger connections) {
        List<Socket> open = new ArrayList<>();
        Pattern key = Pattern.compile("Sec-WebSocket-Key: *(\\S+)", Pattern.CASE_INSENSITIVE);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = server.accept();
                open.add(socket);
                InputStream in = socket.getInputStream();
                StringBuilder request = new StringBuilder();
                while (!request.toString().contains("\r\n\r\n")) {
                    int next = in.read();
                    if (next < 0) {
                        break;
                    }
                    request.append((char) next);
                }
                Matcher matcher = key.matcher(request);
                if (!matcher.find()) {
                    continue;
                }
                byte[] digest = MessageDigest.getInstance("SHA-1").digest(
                    (matcher.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + Base64.getEncoder().encodeToString(digest) + "\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
                out.flush();
                connections.incrementAndGet();
            }
        } catch (Exception ignored) {
            // server socket closed
        } finally {
            for (Socket socket : open) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
