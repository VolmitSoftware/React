package art.arcane.react.web;

import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebSecret;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.controller.IntegrationController.Health;
import art.arcane.react.core.controller.IntegrationController.IntegrationStatus;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.core.history.HistoryPoint;
import art.arcane.react.core.history.HistoryQueryResult;
import art.arcane.react.core.history.HistoryQuerySeries;
import art.arcane.react.core.history.MetricDescriptor;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.util.project.registry.Registry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebControllerIntegrationTest {

    private WebController controller;
    private final List<String> injectedGraphKeys = new ArrayList<>();

    private WebController buildController(WebConfiguration config, File dataFolder, SampleController sampleController) {
        return buildController(config, dataFolder, sampleController, null);
    }

    private WebController buildController(
        WebConfiguration config,
        File dataFolder,
        SampleController sampleController,
        HistoryController historyController
    ) {
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
        c.setHistoryController(historyController);
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
    void webControllerServesRoutesWithAuth(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord record = new TokenRecord("tok-web-1", "test-device", 1000L, Set.of("read"), "viewer");
        String bearer = PairingToken.mint(secret, record.id(), record.label(), record.issuedAt(), record.scopes());
        File tokens = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokens);
        store.add(record);
        store.save(tokens);

        FakeSampler s1 = new FakeSampler("wc-tick-time", 42.0, "ms", new double[]{40.0, 41.0, 42.0});
        FakeSampler s2 = new FakeSampler("wc-tps", 20.0, "", new double[]{20.0, 20.0, 20.0});

        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of(s1, s2));
        when(sampleController.getSampler("wc-tick-time")).thenReturn(s1);

        HistoryController historyController = mock(HistoryController.class);
        when(historyController.latest()).thenReturn(MetricSnapshot.empty());
        when(historyController.descriptors()).thenReturn(List.of(
            new MetricDescriptor("wc-tick-time", "wc-tick-time", "ms", 1_000L, 3_000L, true)
        ));
        when(historyController.effectiveMaxQuerySeries()).thenReturn(16);
        when(historyController.effectiveMaxQueryPoints()).thenReturn(4_096);
        when(historyController.effectiveQueryPagePoints()).thenReturn(256);
        when(historyController.knowsMetric("wc-tick-time")).thenReturn(true);
        when(historyController.knowsMetric("does-not-exist")).thenReturn(false);
        when(historyController.selectResolution(anyLong(), anyLong(), anyInt())).thenReturn(1_000L);
        when(historyController.query(anyList(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong()))
            .thenReturn(new HistoryQueryResult(
                1_000L,
                3_000L,
                1_000L,
                7L,
                3_000L,
                List.of(new HistoryQuerySeries(
                    "wc-tick-time",
                    "wc-tick-time",
                    "ms",
                    List.of(new HistoryPoint(1_000L, 1_000L, 42D, 40D, 43D, 84D, 42D, 2L))
                ))
            ));

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);

        controller = buildController(config, dataFolder, sampleController, historyController);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest noAuthRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/identity"))
            .GET()
            .build();
        HttpResponse<String> noAuthResponse = client.send(noAuthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noAuthResponse.statusCode());

        HttpRequest identityRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/identity"))
            .header("Authorization", "Bearer " + bearer)
            .GET()
            .build();
        HttpResponse<String> identityResponse = client.send(identityRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, identityResponse.statusCode());
        JsonObject identityData = JsonParser.parseString(identityResponse.body())
            .getAsJsonObject().getAsJsonObject("data");
        assertTrue(identityData.has("version"), "identity.data should carry version");
        assertTrue(identityData.has("serverName"), "identity.data should carry serverName");
        assertTrue(identityData.has("folia"), "identity.data should carry folia");
        assertTrue(identityData.has("serverId"), "identity.data should carry serverId");
        assertEquals("9.9.9-test", identityData.get("version").getAsString());
        assertEquals("TestBrand", identityData.get("serverName").getAsString());

        HttpRequest metricsRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/metrics"))
            .header("Authorization", "Bearer " + bearer)
            .GET()
            .build();
        HttpResponse<String> metricsResponse = client.send(metricsRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, metricsResponse.statusCode());
        assertEquals(2, parseSamplerArrayLength(metricsResponse.body()),
            "Expected 2 samplers in scalar snapshot, body was: " + metricsResponse.body());
        JsonObject firstSampler = JsonParser.parseString(metricsResponse.body())
            .getAsJsonObject().getAsJsonObject("data").getAsJsonArray("samplers").get(0).getAsJsonObject();
        assertTrue(firstSampler.has("display"), "each sampler in the metrics array must carry a display string");
        assertTrue(firstSampler.has("available"), "each sampler must expose availability");
        assertTrue(!firstSampler.has("history"), "live snapshots must not duplicate historical arrays");

        HttpRequest historyRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port
                + "/api/v1/metrics/history?ids=wc-tick-time&from=1000&to=3000"))
            .header("Authorization", "Bearer " + bearer)
            .GET()
            .build();
        HttpResponse<String> historyResponse = client.send(historyRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, historyResponse.statusCode());
        JsonObject historyData = JsonParser.parseString(historyResponse.body())
            .getAsJsonObject().getAsJsonObject("data");
        assertEquals(1_000L, historyData.get("actualResolutionMs").getAsLong());
        JsonObject historySeries = historyData.getAsJsonArray("series").get(0).getAsJsonObject();
        assertEquals("wc-tick-time", historySeries.get("id").getAsString());
        assertEquals(6, historySeries.getAsJsonArray("points").get(0).getAsJsonArray().size());

        HttpRequest catalogRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/metrics/catalog"))
            .header("Authorization", "Bearer " + bearer)
            .GET()
            .build();
        HttpResponse<String> catalogResponse = client.send(catalogRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, catalogResponse.statusCode());
        assertEquals(1, parseDataArrayLength(catalogResponse.body()));

        HttpRequest unknownHistory = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port
                + "/api/v1/metrics/history?ids=does-not-exist&from=1000&to=3000"))
            .header("Authorization", "Bearer " + bearer)
            .GET()
            .build();
        HttpResponse<String> unknownResponse = client.send(unknownHistory, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, unknownResponse.statusCode());

        HttpRequest historyNoAuth = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port
                + "/api/v1/metrics/history?ids=wc-tick-time&from=1000&to=3000"))
            .GET()
            .build();
        HttpResponse<String> historyNoAuthResponse = client.send(historyNoAuth, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, historyNoAuthResponse.statusCode());

        HttpRequest preflight = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/metrics"))
            .header("Origin", "http://localhost:8088")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "authorization")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> preflightResponse = client.send(preflight, HttpResponse.BodyHandlers.ofString());
        assertTrue(preflightResponse.statusCode() < 400,
            "CORS preflight (OPTIONS) must succeed even with requireTokenForReads=true; got "
                + preflightResponse.statusCode());

        controller.stop();
        controller = null;

        int closedPort = port;
        assertThrows(IOException.class, () -> {
            HttpRequest afterStop = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + closedPort + "/api/v1/identity"))
                .GET()
                .build();
            client.send(afterStop, HttpResponse.BodyHandlers.ofString());
        });
    }

    @Test
    void readRoutesOpenWhenReadAuthDisabled(@TempDir File dataFolder) throws Exception {
        FakeSampler s1 = new FakeSampler("wc-ra-tick", 10.0, "ms", new double[]{10.0, 11.0});
        FakeSampler s2 = new FakeSampler("wc-ra-tps", 20.0, "", new double[]{20.0, 20.0});

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

        controller = buildController(config, dataFolder, sampleController);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest metricsNoAuth = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/metrics"))
            .GET()
            .build();
        HttpResponse<String> metricsResponse = client.send(metricsNoAuth, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, metricsResponse.statusCode(),
            "Metrics should be accessible without auth when requireTokenForReads=false");
        assertEquals(2, parseSamplerArrayLength(metricsResponse.body()),
            "Expected 2 samplers in scalar snapshot");

        HttpRequest identityNoAuth = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/identity"))
            .GET()
            .build();
        HttpResponse<String> identityResponse = client.send(identityNoAuth, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, identityResponse.statusCode(),
            "Identity should gate on requireTokenForReads identically to the other read routes");
        JsonObject identityData = JsonParser.parseString(identityResponse.body())
            .getAsJsonObject().getAsJsonObject("data");
        assertTrue(identityData.has("version"), "identity.data should carry version");
    }

    @Test
    void webControllerServesIntegrationRouteWithAuth(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord record = new TokenRecord("tok-int-1", "test-device", 1000L, Set.of("read"), "viewer");
        String bearer = PairingToken.mint(secret, record.id(), record.label(), record.issuedAt(), record.scopes());
        File tokens = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokens);
        store.add(record);
        store.save(tokens);

        IntegrationController mockIc = mock(IntegrationController.class);
        when(mockIc.statuses()).thenReturn(List.of(
            new IntegrationStatus("iris", Health.HEALTHY, "v1", 999L, "ok")
        ));
        when(mockIc.recentTimeline(anyInt())).thenReturn(List.of("5s ago - iris bound"));

        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);

        HistoryController historyController = mock(HistoryController.class);
        when(historyController.latest()).thenReturn(MetricSnapshot.empty());
        when(historyController.effectiveMaxQuerySeries()).thenReturn(16);
        when(historyController.effectiveMaxQueryPoints()).thenReturn(4_096);
        when(historyController.effectiveQueryPagePoints()).thenReturn(256);
        when(historyController.knowsMetric("does-not-exist")).thenReturn(false);
        when(historyController.selectResolution(anyLong(), anyLong(), anyInt())).thenReturn(1_000L);
        controller = buildController(config, dataFolder, sampleController, historyController);
        controller.setIntegrationController(mockIc);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest noAuthRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/integrations"))
            .GET()
            .build();
        HttpResponse<String> noAuthResponse = client.send(noAuthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noAuthResponse.statusCode(),
            "Integrations endpoint must return 401 without token when requireTokenForReads=true");

        HttpRequest authRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/integrations"))
            .header("Authorization", "Bearer " + bearer)
            .GET()
            .build();
        HttpResponse<String> authResponse = client.send(authRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, authResponse.statusCode(),
            "Integrations endpoint must return 200 with valid token; body: " + authResponse.body());

        JsonObject root = JsonParser.parseString(authResponse.body()).getAsJsonObject();
        JsonObject data = root.getAsJsonObject("data");
        assertTrue(data.has("integrations"), "data must carry integrations array");
        assertTrue(data.has("timeline"), "data must carry timeline array");

        JsonArray integrations = data.getAsJsonArray("integrations");
        assertEquals(1, integrations.size(), "Expected 1 integration entry");
        JsonObject first = integrations.get(0).getAsJsonObject();
        assertEquals("iris", first.get("pluginId").getAsString());
        assertEquals("HEALTHY", first.get("health").getAsString());

        JsonArray timeline = data.getAsJsonArray("timeline");
        assertEquals(1, timeline.size(), "Expected 1 timeline entry");
    }

    @Test
    void errorResponsesUseErrorEnvelope(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord record = new TokenRecord("tok-err-1", "test-device", 1000L, Set.of("read"), "viewer");
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
        when(sampleController.getSampler("does-not-exist")).thenReturn(null);

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);

        HistoryController historyController = mock(HistoryController.class);
        when(historyController.latest()).thenReturn(MetricSnapshot.empty());
        when(historyController.effectiveMaxQuerySeries()).thenReturn(16);
        when(historyController.effectiveMaxQueryPoints()).thenReturn(4_096);
        when(historyController.effectiveQueryPagePoints()).thenReturn(256);
        when(historyController.knowsMetric("does-not-exist")).thenReturn(false);
        when(historyController.selectResolution(anyLong(), anyLong(), anyInt())).thenReturn(1_000L);
        controller = buildController(config, dataFolder, sampleController, historyController);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest notFoundRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port
                + "/api/v1/metrics/history?ids=does-not-exist&from=1000&to=3000"))
            .header("Authorization", "Bearer " + bearer)
            .GET()
            .build();
        HttpResponse<String> notFoundResponse = client.send(notFoundRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, notFoundResponse.statusCode());
        JsonObject notFoundBody = JsonParser.parseString(notFoundResponse.body()).getAsJsonObject();
        assertTrue(notFoundBody.has("error"), "404 body must have 'error' object, got: " + notFoundResponse.body());
        assertTrue(notFoundBody.getAsJsonObject("error").has("message"),
            "404 error object must have 'message' string, got: " + notFoundResponse.body());

        HttpRequest noAuthRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/identity"))
            .GET()
            .build();
        HttpResponse<String> noAuthResponse = client.send(noAuthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noAuthResponse.statusCode());
        JsonObject noAuthBody = JsonParser.parseString(noAuthResponse.body()).getAsJsonObject();
        assertTrue(noAuthBody.has("error"), "401 body must have 'error' object, got: " + noAuthResponse.body());
        assertTrue(noAuthBody.getAsJsonObject("error").has("message"),
            "401 error object must have 'message' string, got: " + noAuthResponse.body());
    }

    private int parseDataArrayLength(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray data = root.getAsJsonArray("data");
        if (data == null) {
            return 0;
        }
        return data.size();
    }

    private int parseSamplerArrayLength(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject data = root.getAsJsonObject("data");
        if (data == null) {
            return 0;
        }
        JsonArray samplers = data.getAsJsonArray("samplers");
        return samplers == null ? 0 : samplers.size();
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
        lastPushField.set(g, Long.MAX_VALUE);
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
