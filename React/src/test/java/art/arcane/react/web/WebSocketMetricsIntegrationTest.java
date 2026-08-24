package art.arcane.react.web;

import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebSecret;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.util.project.registry.Registry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebSocketMetricsIntegrationTest {

    private WebController controller;
    private final List<String> injectedGraphKeys = new ArrayList<>();

    private WebController buildController(WebConfiguration config, File dataFolder, SampleController sampleController) {
        WebController c = new WebController() {
            @Override
            protected void executeAsync(Runnable r) {
                Thread.ofVirtual().start(r);
            }

            @Override
            protected IdentityDto resolveIdentity() {
                IdentityDto dto = new IdentityDto();
                dto.version = "9.9.9-test";
                dto.serverName = "TestBrand";
                dto.folia = true;
                dto.serverId = "127.0.0.1:0";
                return dto;
            }
        };
        c.setConfig(config);
        c.setDataFolder(dataFolder);
        c.setSampleController(sampleController);
        return c;
    }

    @AfterEach
    void cleanup() throws Exception {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
        Field graphsField = Graph.class.getDeclaredField("graphs");
        graphsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Graph> graphsMap = (ConcurrentHashMap<String, Graph>) graphsField.get(null);
        for (String key : injectedGraphKeys) {
            graphsMap.remove(key);
        }
        injectedGraphKeys.clear();
    }

    @Test
    void it_pushes_live_metrics_frame_to_authorized_socket(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord record = new TokenRecord("tok-ws-1", "ws-device", 1000L, Set.of("read"), "viewer");
        String bearer = PairingToken.mint(secret, record.id(), record.label(), record.issuedAt(), record.scopes());
        File tokens = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokens);
        store.add(record);
        store.save(tokens);

        FakeSampler s1 = new FakeSampler("ws-tick-time", 42.0, "ms", new double[]{40.0, 41.0, 42.0});
        FakeSampler s2 = new FakeSampler("ws-tps", 20.0, "", new double[]{20.0, 20.0, 20.0});

        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of(s1, s2));

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setWsPushHz(20);

        controller = buildController(config, dataFolder, sampleController);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        CountDownLatch textLatch = new CountDownLatch(1);
        AtomicReference<String> firstFrame = new AtomicReference<>(null);

        HttpClient client = HttpClient.newHttpClient();
        URI wsUri = URI.create("ws://127.0.0.1:" + port + "/ws/metrics");

        CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
            .buildAsync(wsUri, new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    webSocket.request(Long.MAX_VALUE);
                    webSocket.sendText(authFrame(bearer), true);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    if (firstFrame.compareAndSet(null, data.toString())) {
                        textLatch.countDown();
                    }
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    textLatch.countDown();
                }
            });

        WebSocket ws = wsFuture.get(5, TimeUnit.SECONDS);

        boolean received = textLatch.await(5, TimeUnit.SECONDS);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(2, TimeUnit.SECONDS);

        assertTrue(received, "Expected a text frame within 5s but none arrived");

        String frame = firstFrame.get();
        JsonObject root = JsonParser.parseString(frame).getAsJsonObject();
        assertTrue(root.has("data"), "Frame must have a 'data' field; got: " + frame);
        JsonArray data = root.getAsJsonArray("data");
        assertEquals(2, data.size(), "Expected data array of length 2; got: " + data.size());

        JsonObject firstSampler = data.get(0).getAsJsonObject();
        assertTrue(firstSampler.has("id"), "Sampler entry must have 'id'; got: " + firstSampler);
        assertTrue(firstSampler.has("value"), "Sampler entry must have 'value'; got: " + firstSampler);
        assertTrue(firstSampler.has("history"), "Sampler entry must have 'history'; got: " + firstSampler);
    }

    @Test
    void it_closes_unauthorized_socket(@TempDir File dataFolder) throws Exception {
        FakeSampler s1 = new FakeSampler("ws-unauth-tick", 10.0, "ms", new double[]{10.0, 11.0});
        FakeSampler s2 = new FakeSampler("ws-unauth-tps", 20.0, "", new double[]{20.0, 20.0});

        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of(s1, s2));

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(true);
        config.setWsPushHz(20);

        controller = buildController(config, dataFolder, sampleController);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        CountDownLatch closeLatch = new CountDownLatch(1);
        AtomicInteger closeCode = new AtomicInteger(-1);
        AtomicInteger textFrameCount = new AtomicInteger(0);

        HttpClient client = HttpClient.newHttpClient();
        URI wsUri = URI.create("ws://127.0.0.1:" + port + "/ws/metrics");

        CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
            .buildAsync(wsUri, new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    webSocket.request(Long.MAX_VALUE);
                    webSocket.sendText(authFrame("invalid-bearer"), true);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    textFrameCount.incrementAndGet();
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    closeCode.set(statusCode);
                    closeLatch.countDown();
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    closeLatch.countDown();
                }
            });

        wsFuture.get(5, TimeUnit.SECONDS);

        boolean closed = closeLatch.await(5, TimeUnit.SECONDS);
        assertTrue(closed, "Expected close event within 5s for unauthorized connection");
        assertEquals(1008, closeCode.get(), "Expected WS close code 1008 for unauthorized; got: " + closeCode.get());
        assertEquals(0, textFrameCount.get(), "Unauthorized socket must receive no text frames");
    }

    @Test
    void it_allows_socket_when_read_auth_disabled(@TempDir File dataFolder) throws Exception {
        FakeSampler s1 = new FakeSampler("ws-open-tick", 15.0, "ms", new double[]{14.0, 15.0, 16.0});
        FakeSampler s2 = new FakeSampler("ws-open-tps", 20.0, "", new double[]{20.0, 20.0, 20.0});

        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of(s1, s2));

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(false);
        config.setWsPushHz(20);

        controller = buildController(config, dataFolder, sampleController);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        CountDownLatch textLatch = new CountDownLatch(1);
        AtomicReference<String> firstFrame = new AtomicReference<>(null);

        HttpClient client = HttpClient.newHttpClient();
        URI wsUri = URI.create("ws://127.0.0.1:" + port + "/ws/metrics");

        CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
            .buildAsync(wsUri, new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    webSocket.request(Long.MAX_VALUE);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    if (firstFrame.compareAndSet(null, data.toString())) {
                        textLatch.countDown();
                    }
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    textLatch.countDown();
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    textLatch.countDown();
                }
            });

        WebSocket ws = wsFuture.get(5, TimeUnit.SECONDS);

        boolean received = textLatch.await(5, TimeUnit.SECONDS);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(2, TimeUnit.SECONDS);

        assertTrue(received, "Expected a text frame within 5s for open (no auth) connection");
        assertFalse(firstFrame.get() == null, "Must have received at least one non-null frame");
    }

    @Test
    void query_bearer_does_not_authenticate_without_first_frame(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord record = new TokenRecord("tok-ws-query", "query-device", 1000L, Set.of("read"), "viewer");
        String bearer = PairingToken.mint(secret, record.id(), record.label(), record.issuedAt(), record.scopes());
        File tokens = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokens);
        store.add(record);
        store.save(tokens);

        FakeSampler sampler = new FakeSampler("ws-query-tick", 12.0, "ms", new double[]{12.0});
        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of(sampler));

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(true);
        config.setWsPushHz(20);

        controller = buildController(config, dataFolder, sampleController);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        CountDownLatch textLatch = new CountDownLatch(1);
        AtomicInteger textFrames = new AtomicInteger();
        HttpClient client = HttpClient.newHttpClient();
        URI wsUri = URI.create(
            "ws://127.0.0.1:" + controller.getBoundPort() + "/ws/metrics?token=" + bearer
        );
        WebSocket socket = client.newWebSocketBuilder()
            .buildAsync(wsUri, new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    webSocket.request(Long.MAX_VALUE);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    textFrames.incrementAndGet();
                    textLatch.countDown();
                    return null;
                }
            })
            .get(5, TimeUnit.SECONDS);

        Thread.sleep(250L);
        assertEquals(0, controller.getMetricsSessions().size());
        assertEquals(0, textFrames.get());

        socket.sendText(authFrame(bearer), true).get(2, TimeUnit.SECONDS);
        assertTrue(textLatch.await(5, TimeUnit.SECONDS));
        assertEquals(1, controller.getMetricsSessions().size());
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(2, TimeUnit.SECONDS);
    }

    @Test
    void malformed_and_repeated_auth_frames_close_with_1008(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord record = new TokenRecord("tok-ws-policy", "policy-device", 1000L, Set.of("read"), "viewer");
        String bearer = PairingToken.mint(secret, record.id(), record.label(), record.issuedAt(), record.scopes());
        File tokens = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokens);
        store.add(record);
        store.save(tokens);

        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(true);
        controller = buildController(config, dataFolder, sampleController);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000L);

        URI uri = URI.create("ws://127.0.0.1:" + controller.getBoundPort() + "/ws/metrics");
        assertEquals(1008, closeCodeForFrames(uri, List.of("{\"type\":\"auth\"}")));
        assertEquals(1008, closeCodeForFrames(uri, List.of(authFrame(bearer), authFrame(bearer))));
    }

    private static int closeCodeForFrames(URI uri, List<String> frames) throws Exception {
        CountDownLatch closeLatch = new CountDownLatch(1);
        AtomicInteger closeCode = new AtomicInteger(-1);
        HttpClient.newHttpClient().newWebSocketBuilder()
            .buildAsync(uri, new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    webSocket.request(Long.MAX_VALUE);
                    for (String frame : frames) {
                        webSocket.sendText(frame, true);
                    }
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    closeCode.set(statusCode);
                    closeLatch.countDown();
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    closeLatch.countDown();
                }
            })
            .get(5, TimeUnit.SECONDS);
        assertTrue(closeLatch.await(5, TimeUnit.SECONDS));
        return closeCode.get();
    }

    private static String authFrame(String bearer) {
        JsonObject frame = new JsonObject();
        frame.addProperty("type", "auth");
        frame.addProperty("token", bearer);
        return frame.toString();
    }

    private Graph injectGraph(String name, double[] history) throws Exception {
        Field graphsField = Graph.class.getDeclaredField("graphs");
        graphsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Graph> graphsMap = (ConcurrentHashMap<String, Graph>) graphsField.get(null);
        Graph g = new Graph();
        Field seqField = Graph.class.getDeclaredField("sequence");
        seqField.setAccessible(true);
        double[] seq = (double[]) seqField.get(g);
        for (int i = 0; i < history.length; i++) {
            seq[i] = history[i];
        }
        Field headField = Graph.class.getDeclaredField("head");
        headField.setAccessible(true);
        headField.set(g, history.length);
        Field sizeField = Graph.class.getDeclaredField("size");
        sizeField.setAccessible(true);
        sizeField.set(g, history.length);
        Field lastPushField = Graph.class.getDeclaredField("lastPushMs");
        lastPushField.setAccessible(true);
        lastPushField.set(g, System.currentTimeMillis());
        graphsMap.put(name, g);
        injectedGraphKeys.add(name);
        return g;
    }

    private final class FakeSampler implements Sampler {
        private final String id;
        private final double value;
        private final String suffix;

        FakeSampler(String id, double value, String suffix, double[] history) {
            this.id = id;
            this.value = value;
            this.suffix = suffix;
            try {
                injectGraph(id, history);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
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
        public double sample() {
            return value;
        }

        @Override
        public String formattedValue(double t) {
            return String.valueOf(t);
        }

        @Override
        public String formattedSuffix(double t) {
            return suffix;
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void render() {}
    }
}
