package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.RingLogHandler;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LogsRoutesIntegrationTest {

    private WebController controller;

    private WebController buildController(WebConfiguration config, File dataFolder) {
        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());

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
                dto.folia = false;
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
    void cleanup() {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
    }

    @Test
    void get_logs_without_token_returns_401(@TempDir File dataFolder) throws Exception {
        WebConfiguration config = new WebConfiguration();
        config.setEnabled(true);
        config.setBindAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(true);

        WebSecret.load(dataFolder);
        File tokensFile = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokensFile);
        store.save(tokensFile);

        controller = buildController(config, dataFolder);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/logs"))
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode(),
            "GET /api/v1/logs without token should return 401, body: " + response.body());
    }

    @Test
    void get_logs_with_read_token_returns_200_and_data_array(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord readRecord = new TokenRecord("tok-read-logs", "read-device", 1000L, Set.of("read"), "viewer");
        String readBearer = PairingToken.mint(secret, readRecord.id(), readRecord.label(), readRecord.issuedAt(), readRecord.scopes());
        File tokensFile = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokensFile);
        store.add(readRecord);
        store.save(tokensFile);

        WebConfiguration config = new WebConfiguration();
        config.setEnabled(true);
        config.setBindAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(true);

        controller = buildController(config, dataFolder);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        RingLogHandler handler = controller.getLogHandler();
        assertNotNull(handler, "WebController must expose logHandler after start");
        handler.publish(new LogRecord(Level.INFO, "seeded-log-line"));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/logs"))
            .header("Authorization", "Bearer " + readBearer)
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(),
            "GET /api/v1/logs with read token should return 200, body: " + response.body());

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        assertTrue(root.has("data"), "Response must have 'data' field; got: " + response.body());
        JsonArray data = root.getAsJsonArray("data");
        assertNotNull(data, "data must be a JSON array");

        boolean found = false;
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getAsString().contains("seeded-log-line")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "data array must contain the seeded log line; got: " + data);
    }

    @Test
    void ws_logs_delivers_frame_to_authorized_client(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord readRecord = new TokenRecord("tok-ws-logs", "ws-device", 1000L, Set.of("read"), "viewer");
        String readBearer = PairingToken.mint(secret, readRecord.id(), readRecord.label(), readRecord.issuedAt(), readRecord.scopes());
        File tokensFile = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokensFile);
        store.add(readRecord);
        store.save(tokensFile);

        WebConfiguration config = new WebConfiguration();
        config.setEnabled(true);
        config.setBindAddress("127.0.0.1");
        config.setPort(0);

        controller = buildController(config, dataFolder);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        CountDownLatch textLatch = new CountDownLatch(1);
        AtomicReference<String> firstFrame = new AtomicReference<>(null);

        HttpClient client = HttpClient.newHttpClient();
        URI wsUri = URI.create("ws://127.0.0.1:" + port + "/ws/logs?token=" + readBearer);

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

        wsFuture.get(5, TimeUnit.SECONDS);

        Thread.sleep(200);

        RingLogHandler handler = controller.getLogHandler();
        assertNotNull(handler, "WebController must expose logHandler after start");
        handler.publish(new LogRecord(Level.INFO, "ws-log-line"));

        boolean received = textLatch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Expected a text frame within 5s after publishing a log record");

        String frame = firstFrame.get();
        assertNotNull(frame, "Received frame must not be null");

        JsonObject root = JsonParser.parseString(frame).getAsJsonObject();
        assertTrue(root.has("type"), "Frame must have 'type' field; got: " + frame);
        assertTrue(root.has("line"), "Frame must have 'line' field; got: " + frame);
        assertEquals("log", root.get("type").getAsString(), "type must be 'log'; got: " + frame);
        assertTrue(root.get("line").getAsString().contains("ws-log-line"),
            "line must contain 'ws-log-line'; got: " + frame);
    }

    @Test
    void ws_logs_unauthorized_closes_with_1008(@TempDir File dataFolder) throws Exception {
        WebConfiguration config = new WebConfiguration();
        config.setEnabled(true);
        config.setBindAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(true);

        WebSecret.load(dataFolder);
        File tokensFile = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokensFile);
        store.save(tokensFile);

        controller = buildController(config, dataFolder);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        CountDownLatch closeLatch = new CountDownLatch(1);
        AtomicInteger closeCode = new AtomicInteger(-1);
        AtomicInteger textFrameCount = new AtomicInteger(0);

        HttpClient client = HttpClient.newHttpClient();
        URI wsUri = URI.create("ws://127.0.0.1:" + port + "/ws/logs");

        CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
            .buildAsync(wsUri, new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    webSocket.request(Long.MAX_VALUE);
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
        assertTrue(closed, "Expected close event within 5s for unauthorized WS connection");
        assertEquals(1008, closeCode.get(),
            "Expected WS close code 1008 for unauthorized; got: " + closeCode.get());
        assertEquals(0, textFrameCount.get(), "Unauthorized socket must receive no text frames");
    }
}
