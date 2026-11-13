package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.ActionTicketRegistry;
import art.arcane.react.api.web.RingLogHandler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebRevisions;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.util.project.registry.Registry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveSyncIntegrationTest {
    private static final int BURST = 500;

    private WebController controller;
    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void cleanup() {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
    }

    @Test
    void logBurstArrivesInOrderWithoutLossAsSingleLineFrames(@TempDir File dataFolder) throws Exception {
        String bearer = start(dataFolder, 15_000L, List.of(), "admin");
        Client client = Client.open(http, uri("/ws/logs"), bearer);
        awaitSessions(() -> controller.getLogSessions().size(), 1);

        publishBurst("single");

        List<JsonObject> frames = client.awaitFrames(json -> json.get("line").getAsString().contains("single-" + (BURST - 1)));
        List<String> lines = new ArrayList<>();
        long previousSeq = -1L;
        for (JsonObject frame : frames) {
            assertEquals("log", frame.get("type").getAsString());
            long seq = frame.get("seq").getAsLong();
            assertTrue(seq > previousSeq, "seq must rise frame by frame");
            previousSeq = seq;
            String line = frame.get("line").getAsString();
            if (line.contains("burst-single-")) {
                lines.add(line.substring(line.indexOf("burst-single-")));
            }
        }
        assertEquals(expectedBurst("single"), lines);
        client.close();
    }

    @Test
    void logBurstArrivesInOrderWithoutLossAsBatchedFrames(@TempDir File dataFolder) throws Exception {
        String bearer = start(dataFolder, 15_000L, List.of(), "admin");
        Client client = Client.open(http, uri("/ws/logs?batch=1"), bearer);
        awaitSessions(() -> controller.getLogSessions().size(), 1);

        publishBurst("batched");

        List<JsonObject> frames = client.awaitFrames(json -> json.toString().contains("batched-" + (BURST - 1)));
        List<String> lines = new ArrayList<>();
        long previousSeq = -1L;
        for (JsonObject frame : frames) {
            assertEquals("logs", frame.get("type").getAsString());
            assertFalse(frame.has("dropped"), "nothing may be dropped under the bound");
            for (JsonElement element : frame.getAsJsonArray("lines")) {
                JsonObject entry = element.getAsJsonObject();
                long seq = entry.get("seq").getAsLong();
                if (previousSeq >= 0L) {
                    assertEquals(previousSeq + 1L, seq, "seq must be contiguous");
                }
                previousSeq = seq;
                String line = entry.get("line").getAsString();
                if (line.contains("burst-batched-")) {
                    lines.add(line.substring(line.indexOf("burst-batched-")));
                }
            }
        }
        assertEquals(expectedBurst("batched"), lines);
        assertTrue(frames.size() < BURST, "a burst must be batched into fewer frames than lines");
        client.close();
    }

    @Test
    void logsEndpointReportsTheSeqOfItsLastLine(@TempDir File dataFolder) throws Exception {
        String bearer = start(dataFolder, 15_000L, List.of(), "admin");
        RingLogHandler handler = controller.getLogHandler();
        handler.publishExternal("INFO", "gap-a\ngap-b", null);

        HttpResponse<String> response = get("/api/v1/logs?limit=2", bearer);
        assertEquals(200, response.statusCode(), response.body());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray data = root.getAsJsonArray("data");
        assertEquals(2, data.size());
        assertTrue(data.get(1).getAsString().endsWith("gap-b"));
        assertEquals(handler.tail(1).lastSeq(), root.get("lastSeq").getAsLong());
    }

    @Test
    void serverPingsAuthenticatedSocketsWhileIdle(@TempDir File dataFolder) throws Exception {
        String bearer = start(dataFolder, 100L, List.of(), "admin");
        Client logs = Client.open(http, uri("/ws/logs"), bearer);
        Client metrics = Client.open(http, uri("/ws/metrics"), bearer);
        awaitSessions(() -> controller.getLogSessions().size(), 1);
        awaitSessions(() -> controller.getMetricsSessions().size(), 1);

        assertTrue(logs.awaitPings(3, 5_000L), "log socket must be pinged while idle");
        assertTrue(metrics.awaitPings(3, 5_000L), "metrics socket must be pinged");
        logs.close();
        metrics.close();
    }

    @Test
    void revokeClosesOpenSocketsWith4401AndRestAnswersTokenRevoked(@TempDir File dataFolder) throws Exception {
        String bearer = start(dataFolder, 15_000L, List.of(new FakeSampler("live-tps", 20D, true)), "admin");
        Client logs = Client.open(http, uri("/ws/logs"), bearer);
        Client metrics = Client.open(http, uri("/ws/metrics"), bearer);
        awaitSessions(() -> controller.getLogSessions().size(), 1);
        awaitSessions(() -> controller.getMetricsSessions().size(), 1);

        assertEquals(List.of(tokenId(bearer)), controller.revoke(tokenId(bearer)));

        assertEquals(4401, logs.awaitClose(5_000L));
        assertEquals(4401, metrics.awaitClose(5_000L));
        HttpResponse<String> response = get("/api/v1/metrics", bearer);
        assertEquals(401, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("token_revoked", body.getAsJsonObject("error").get("code").getAsString());
        assertTrue(controller.revoke(tokenId(bearer)).isEmpty(), "a second revoke finds nothing");
    }

    @Test
    void staleCounterConflictCarriesTheLastAcceptedCounter(@TempDir File dataFolder) throws Exception {
        String bearer = start(dataFolder, 15_000L, List.of(), "admin");

        HttpResponse<String> accepted = put("/api/v1/tweaks/not-a-tweak", bearer, 5_000L);
        assertTrue(accepted.statusCode() != 409, accepted.body());
        HttpResponse<String> stale = put("/api/v1/tweaks/not-a-tweak", bearer, 4_000L);

        assertEquals(409, stale.statusCode());
        JsonObject body = JsonParser.parseString(stale.body()).getAsJsonObject();
        assertEquals("counter_conflict", body.getAsJsonObject("error").get("code").getAsString());
        assertEquals(5_000L, body.getAsJsonObject("error").get("lastCounter").getAsLong());
        HttpResponse<String> rebased = put("/api/v1/tweaks/not-a-tweak", bearer, 5_001L);
        assertTrue(rebased.statusCode() != 409, "a counter above lastCounter is accepted again");
    }

    @Test
    void metricsFramesAreCompactAndCarryRevisions(@TempDir File dataFolder) throws Exception {
        FakeSampler live = new FakeSampler("live-tps", 20D, true);
        FakeSampler off = new FakeSampler("live-off", 0D, false);
        String bearer = start(dataFolder, 15_000L, List.of(live, off), "viewer");
        Client metrics = Client.open(http, uri("/ws/metrics"), bearer);

        JsonObject first = metrics.awaitFrames(json -> true).get(0).getAsJsonObject("data");
        JsonArray samplers = first.getAsJsonArray("samplers");
        assertEquals(1, samplers.size(), "unavailable samplers are left out of frames");
        JsonObject sample = samplers.get(0).getAsJsonObject();
        assertEquals(Set.of("id", "name", "suffix", "value", "display"), sample.keySet());
        JsonObject revisions = first.getAsJsonObject("revisions");
        assertEquals(Set.of("features", "tweaks", "config", "worlds", "packs", "actions"), revisions.keySet());

        long tweaksBefore = revisions.get("tweaks").getAsLong();
        long started = System.nanoTime();
        WebRevisions.bump(WebRevisions.Kind.TWEAKS);
        JsonObject moved = metrics.awaitFrames(json -> json.getAsJsonObject("data").getAsJsonObject("revisions")
            .get("tweaks").getAsLong() > tweaksBefore).getLast().getAsJsonObject("data");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertEquals(tweaksBefore + 1L, moved.getAsJsonObject("revisions").get("tweaks").getAsLong());
        assertTrue(elapsedMs < 1_000L, "a revision change must reach the socket within a frame, took " + elapsedMs);

        JsonObject full = JsonParser.parseString(get("/api/v1/metrics", bearer).body()).getAsJsonObject().getAsJsonObject("data");
        assertEquals(2, full.getAsJsonArray("samplers").size(), "REST keeps every sampler");
        assertTrue(full.getAsJsonObject("revisions").has("tweaks"));
        JsonObject compact = JsonParser.parseString(get("/api/v1/metrics?compact=1", bearer).body()).getAsJsonObject().getAsJsonObject("data");
        assertEquals(1, compact.getAsJsonArray("samplers").size());
        assertEquals(5, compact.getAsJsonArray("samplers").get(0).getAsJsonObject().size());
        metrics.close();
    }

    @Test
    void actionTicketsReportTheirState(@TempDir File dataFolder) throws Exception {
        String bearer = start(dataFolder, 15_000L, List.of(), "viewer");
        ActionTicketRegistry tickets = controller.getActionTickets();
        long actionsBefore = WebRevisions.current().actions();
        String id = tickets.register();
        assertTrue(WebRevisions.current().actions() > actionsBefore, "a ticket change bumps the actions revision");

        assertTicket(bearer, id, "queued", "");
        tickets.update(id, ActionTicketRegistry.State.RUNNING, "");
        assertTicket(bearer, id, "running", "");
        tickets.update(id, ActionTicketRegistry.State.DONE, "Collected 12 MB");
        assertTicket(bearer, id, "done", "Collected 12 MB");

        assertEquals(404, get("/api/v1/actions/tickets/unknown", bearer).statusCode());
        assertEquals(401, get("/api/v1/actions/tickets/" + id, null).statusCode());
    }

    private void assertTicket(String bearer, String id, String state, String message) throws Exception {
        HttpResponse<String> response = get("/api/v1/actions/tickets/" + id, bearer);
        assertEquals(200, response.statusCode(), response.body());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(id, body.get("id").getAsString());
        assertEquals(state, body.get("state").getAsString());
        assertEquals(message, body.get("message").getAsString());
    }

    private void publishBurst(String tag) {
        RingLogHandler handler = controller.getLogHandler();
        for (int index = 0; index < BURST; index++) {
            handler.publishExternal("INFO", "burst-" + tag + "-" + index, null);
        }
    }

    private static List<String> expectedBurst(String tag) {
        List<String> expected = new ArrayList<>(BURST);
        for (int index = 0; index < BURST; index++) {
            expected.add("burst-" + tag + "-" + index);
        }
        return expected;
    }

    private String start(File dataFolder, long pingMillis, List<Sampler> samplers, String role) throws Exception {
        String bearer = WebTestTokens.issue(dataFolder, "live-device", role);

        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(samplers);

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(true);
        controller = new WebController() {
            @Override
            protected void executeAsync(Runnable r) {
                Thread.ofVirtual().start(r);
            }

            @Override
            protected long wsPingIntervalMillis() {
                return pingMillis;
            }

            @Override
            protected IdentityDto resolveIdentity() {
                IdentityDto dto = new IdentityDto();
                dto.serverName = "TestBrand";
                dto.serverId = "127.0.0.1:0";
                return dto;
            }
        };
        controller.setConfig(config);
        controller.setDataFolder(dataFolder);
        controller.setSampleController(sampleController);
        controller.start();
        controller.postStart();
        controller.awaitStart(5_000L);
        return bearer;
    }

    private URI uri(String path) {
        return URI.create("ws://127.0.0.1:" + controller.getBoundPort() + path);
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + controller.getBoundPort() + path))
            .timeout(Duration.ofSeconds(5))
            .GET();
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String bearer, long counter) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + controller.getBoundPort() + path))
            .timeout(Duration.ofSeconds(5))
            .header("Authorization", "Bearer " + bearer)
            .header("X-React-Counter", Long.toString(counter))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"enabled\":true}"))
            .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String tokenId(String bearer) {
        return bearer.substring(0, bearer.indexOf('.'));
    }

    private static void awaitSessions(java.util.function.IntSupplier count, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (count.getAsInt() < expected) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("sessions did not reach " + expected);
            }
            Thread.sleep(10L);
        }
    }

    private static final class Client implements WebSocket.Listener {
        private final List<JsonObject> frames = new CopyOnWriteArrayList<>();
        private final StringBuilder partial = new StringBuilder();
        private final AtomicInteger pings = new AtomicInteger();
        private final AtomicInteger closeCode = new AtomicInteger(-1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final String bearer;
        private WebSocket socket;

        private Client(String bearer) {
            this.bearer = bearer;
        }

        static Client open(HttpClient http, URI uri, String bearer) throws Exception {
            Client client = new Client(bearer);
            client.socket = http.newWebSocketBuilder().buildAsync(uri, client).get(5, TimeUnit.SECONDS);
            return client;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(Long.MAX_VALUE);
            JsonObject auth = new JsonObject();
            auth.addProperty("type", "auth");
            auth.addProperty("token", bearer);
            webSocket.sendText(auth.toString(), true);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (partial) {
                partial.append(data);
                if (last) {
                    frames.add(JsonParser.parseString(partial.toString()).getAsJsonObject());
                    partial.setLength(0);
                }
            }
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            pings.incrementAndGet();
            return WebSocket.Listener.super.onPing(webSocket, message);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeCode.set(statusCode);
            closed.countDown();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed.countDown();
        }

        List<JsonObject> awaitFrames(Predicate<JsonObject> done) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                List<JsonObject> snapshot = List.copyOf(frames);
                for (JsonObject frame : snapshot) {
                    if (done.test(frame)) {
                        return snapshot.subList(0, snapshot.indexOf(frame) + 1);
                    }
                }
                Thread.sleep(10L);
            }
            throw new AssertionError("expected frame never arrived; got " + frames.size() + " frames");
        }

        boolean awaitPings(int count, long timeoutMs) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (pings.get() < count) {
                if (System.nanoTime() > deadline) {
                    return false;
                }
                Thread.sleep(10L);
            }
            return true;
        }

        int awaitClose(long timeoutMs) throws InterruptedException {
            assertTrue(closed.await(timeoutMs, TimeUnit.MILLISECONDS), "socket did not close");
            return closeCode.get();
        }

        void close() {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                socket.abort();
            }
        }
    }

    private static final class FakeSampler implements Sampler {
        private final String id;
        private final double value;
        private final boolean available;

        FakeSampler(String id, double value, boolean available) {
            this.id = id;
            this.value = value;
            this.available = available;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return id;
        }

        @Override
        public boolean isSampleAvailable() {
            return available;
        }

        @Override
        public Diagnostics diagnostics() {
            return new Diagnostics(1L, 1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        @Override
        public double sample() {
            return value;
        }

        @Override
        public String formattedValue(double t) {
            return String.valueOf(t);
        }

        @Override
        public String formattedSuffix(double t) {
            return "";
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void render() {
        }
    }
}
