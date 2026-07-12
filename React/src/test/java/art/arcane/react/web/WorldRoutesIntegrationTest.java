package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebSecret;
import art.arcane.react.api.web.WorldBackend;
import art.arcane.react.api.web.dto.WorldDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.api.web.dto.IdentityDto;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WorldRoutesIntegrationTest {

    private WebController controller;

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
    void worldsRoutesHappyPathAndAuthGuards(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord opRecord = new TokenRecord("tok-op", "op-device", 1000L, Set.of("read", "op:execute"), "operator");
        TokenRecord readRecord = new TokenRecord("tok-read", "read-device", 1000L, Set.of("read"), "viewer");
        String opBearer = PairingToken.mint(secret, opRecord.id(), opRecord.label(), opRecord.issuedAt(), opRecord.scopes());
        String readBearer = PairingToken.mint(secret, readRecord.id(), readRecord.label(), readRecord.issuedAt(), readRecord.scopes());
        File tokensFile = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokensFile);
        store.add(opRecord);
        store.add(readRecord);
        store.save(tokensFile);

        FakeWorldBackend fakeWorldBackend = new FakeWorldBackend();

        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());

        WebConfiguration config = new WebConfiguration();
        config.setEnabled(true);
        config.setBindAddress("127.0.0.1");
        config.setPort(0);

        controller = buildController(config, dataFolder, sampleController);
        controller.setWorldBackend(fakeWorldBackend);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();
        AtomicLong opCounter = new AtomicLong(0);

        HttpRequest listRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/worlds"))
            .header("Authorization", "Bearer " + readBearer)
            .GET()
            .build();
        HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode(),
            "GET /api/v1/worlds should return 200, body: " + listResponse.body());
        JsonObject listRoot = JsonParser.parseString(listResponse.body()).getAsJsonObject();
        JsonArray listData = listRoot.getAsJsonArray("data");
        assertNotNull(listData, "Response should contain 'data' array");
        assertEquals(fakeWorldBackend.list().size(), listData.size(),
            "data array length should match fake backend world count");
        JsonObject firstWorld = listData.get(0).getAsJsonObject();
        assertTrue(firstWorld.has("key"), "world entry should have key");
        assertTrue(firstWorld.has("name"), "world entry should have name");
        assertTrue(firstWorld.has("pressureMode"), "world entry should have pressureMode");
        assertTrue(firstWorld.has("budgetMs"), "world entry should have budgetMs");
        assertTrue(firstWorld.has("panicMs"), "world entry should have panicMs");
        assertTrue(firstWorld.has("releaseMs"), "world entry should have releaseMs");

        long c1 = opCounter.incrementAndGet();
        HttpRequest putRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/worlds/update"))
            .header("Authorization", "Bearer " + opBearer)
            .header("X-React-Counter", String.valueOf(c1))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"worldKey\":\"test:world/path\",\"budgetMs\":40}"))
            .build();
        HttpResponse<String> putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, putResponse.statusCode(),
            "PUT /api/v1/worlds/update with op token should return 200, body: " + putResponse.body());
        JsonObject putRoot = JsonParser.parseString(putResponse.body()).getAsJsonObject();
        JsonObject putData = putRoot.getAsJsonObject("data");
        assertNotNull(putData, "Response should contain 'data' object");
        assertTrue(putData.has("name"), "data should have name");
        assertEquals("world", putData.get("name").getAsString());
        assertEquals(40.0, putData.get("budgetMs").getAsDouble(), 1e-9);

        long c2 = opCounter.incrementAndGet();
        HttpRequest putUnknownRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/worlds/update"))
            .header("Authorization", "Bearer " + opBearer)
            .header("X-React-Counter", String.valueOf(c2))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"worldKey\":\"test:no/such/world\",\"budgetMs\":40}"))
            .build();
        HttpResponse<String> putUnknownResponse = client.send(putUnknownRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, putUnknownResponse.statusCode(),
            "PUT /api/v1/worlds/update with an unknown key should return 404, body: " + putUnknownResponse.body());
        JsonObject notFoundBody = JsonParser.parseString(putUnknownResponse.body()).getAsJsonObject();
        assertTrue(notFoundBody.has("error"), "404 body must have 'error' object");
        assertNotNull(notFoundBody.getAsJsonObject("error").get("message"), "error must have 'message'");

        long c3 = opCounter.incrementAndGet();
        HttpRequest putReadRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/worlds/update"))
            .header("Authorization", "Bearer " + readBearer)
            .header("X-React-Counter", String.valueOf(c3))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"worldKey\":\"test:world/path\",\"budgetMs\":40}"))
            .build();
        HttpResponse<String> putReadResponse = client.send(putReadRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, putReadResponse.statusCode(),
            "PUT /api/v1/worlds/update with read-only token should return 403, body: " + putReadResponse.body());
        JsonObject forbiddenBody = JsonParser.parseString(putReadResponse.body()).getAsJsonObject();
        assertTrue(forbiddenBody.has("error"), "403 body must have 'error' object");
        assertNotNull(forbiddenBody.getAsJsonObject("error").get("message"), "error must have 'message'");

        HttpRequest noCounterRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/worlds/update"))
            .header("Authorization", "Bearer " + opBearer)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"worldKey\":\"test:world/path\",\"budgetMs\":40}"))
            .build();
        HttpResponse<String> noCounterResponse = client.send(noCounterRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(409, noCounterResponse.statusCode(),
            "PUT without X-React-Counter should return 409, body: " + noCounterResponse.body());
        JsonObject noCounterBody = JsonParser.parseString(noCounterResponse.body()).getAsJsonObject();
        assertTrue(noCounterBody.has("error"), "409 body must have 'error' object");
        assertNotNull(noCounterBody.getAsJsonObject("error").get("message"), "error must have 'message'");
    }

    private static final class FakeWorldBackend implements WorldBackend {

        private final List<WorldDto> worlds = new ArrayList<>();

        FakeWorldBackend() {
            WorldDto w = new WorldDto();
            w.key = "test:world/path";
            w.name = "world";
            w.pressureMode = "NORMAL";
            w.budgetMs = 35.0;
            w.panicMs = 50.0;
            w.releaseMs = 28.0;
            worlds.add(w);
        }

        @Override
        public List<WorldDto> list() {
            return worlds;
        }

        @Override
        public WorldDto update(String worldKey, Double budgetMs, Double panicMs, Double releaseMs) {
            for (WorldDto w : worlds) {
                if (w.key.equals(worldKey)) {
                    if (budgetMs != null) {
                        w.budgetMs = budgetMs;
                    }
                    if (panicMs != null) {
                        w.panicMs = panicMs;
                    }
                    if (releaseMs != null) {
                        w.releaseMs = releaseMs;
                    }
                    return w;
                }
            }
            return null;
        }
    }
}
