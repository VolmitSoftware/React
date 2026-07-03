package art.arcane.react.web;

import art.arcane.react.api.web.ControlBackend;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebSecret;
import art.arcane.react.api.web.dto.ControlItemDto;
import art.arcane.react.api.sampler.Sampler;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ControlRoutesIntegrationTest {

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
    void featuresRoutesHappyPathAndAuthGuards(@TempDir File dataFolder) throws Exception {
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

        FakeControlBackend fakeFeatureBackend = new FakeControlBackend("feature");
        FakeControlBackend fakeTweakBackend = new FakeControlBackend("tweak");

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
        controller.setFeatureControlBackend(fakeFeatureBackend);
        controller.setTweakControlBackend(fakeTweakBackend);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();
        AtomicLong opCounter = new AtomicLong(0);

        HttpRequest listRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/features"))
            .header("Authorization", "Bearer " + readBearer)
            .GET()
            .build();
        HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode(),
            "GET /api/v1/features with read token should return 200, body: " + listResponse.body());
        JsonObject listRoot = JsonParser.parseString(listResponse.body()).getAsJsonObject();
        JsonArray listData = listRoot.getAsJsonArray("data");
        assertNotNull(listData, "Response should contain 'data' array");
        assertEquals(fakeFeatureBackend.list().size(), listData.size(),
            "data array length should match fake backend item count");

        HttpRequest getKnownRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/features/feature-a"))
            .header("Authorization", "Bearer " + readBearer)
            .GET()
            .build();
        HttpResponse<String> getKnownResponse = client.send(getKnownRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getKnownResponse.statusCode(),
            "GET /api/v1/features/{known-id} should return 200, body: " + getKnownResponse.body());
        JsonObject knownRoot = JsonParser.parseString(getKnownResponse.body()).getAsJsonObject();
        JsonObject knownData = knownRoot.getAsJsonObject("data");
        assertNotNull(knownData, "Response should contain 'data' object");
        assertEquals("feature-a", knownData.get("id").getAsString());
        assertTrue(knownData.has("name"), "data should carry name");
        assertTrue(knownData.has("enabled"), "data should carry enabled");

        HttpRequest getUnknownRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/features/does-not-exist"))
            .header("Authorization", "Bearer " + readBearer)
            .GET()
            .build();
        HttpResponse<String> getUnknownResponse = client.send(getUnknownRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, getUnknownResponse.statusCode(),
            "GET /api/v1/features/{unknown-id} should return 404, body: " + getUnknownResponse.body());
        JsonObject unknownBody = JsonParser.parseString(getUnknownResponse.body()).getAsJsonObject();
        assertTrue(unknownBody.has("error"), "404 body must have 'error' object");
        assertTrue(unknownBody.getAsJsonObject("error").has("message"), "error must have 'message'");

        long c1 = opCounter.incrementAndGet();
        HttpRequest toggleOpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/features/feature-a"))
            .header("Authorization", "Bearer " + opBearer)
            .header("X-React-Counter", String.valueOf(c1))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"enabled\":false}"))
            .build();
        HttpResponse<String> toggleOpResponse = client.send(toggleOpRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, toggleOpResponse.statusCode(),
            "PUT /api/v1/features/{id} with op token should return 200, body: " + toggleOpResponse.body());
        JsonObject toggleData = JsonParser.parseString(toggleOpResponse.body()).getAsJsonObject().getAsJsonObject("data");
        assertNotNull(toggleData, "Response should contain 'data' object");
        assertTrue(fakeFeatureBackend.setEnabledCalled, "setEnabled should have been called");
        assertEquals("feature-a", fakeFeatureBackend.lastSetEnabledId);
        assertEquals(false, fakeFeatureBackend.lastSetEnabledValue);

        AtomicLong readCounter = new AtomicLong(0);
        long rc1 = readCounter.incrementAndGet();
        HttpRequest toggleReadRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/features/feature-a"))
            .header("Authorization", "Bearer " + readBearer)
            .header("X-React-Counter", String.valueOf(rc1))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"enabled\":true}"))
            .build();
        fakeFeatureBackend.setEnabledCalled = false;
        HttpResponse<String> toggleReadResponse = client.send(toggleReadRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, toggleReadResponse.statusCode(),
            "PUT /api/v1/features/{id} with read-only token should return 403, body: " + toggleReadResponse.body());
        assertTrue(!fakeFeatureBackend.setEnabledCalled, "setEnabled must NOT be called when scope check fails");
        JsonObject forbiddenBody = JsonParser.parseString(toggleReadResponse.body()).getAsJsonObject();
        assertTrue(forbiddenBody.has("error"), "403 body must have 'error' object");
        assertTrue(forbiddenBody.getAsJsonObject("error").has("message"), "error must have 'message'");

        HttpRequest noCounterRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/features/feature-a"))
            .header("Authorization", "Bearer " + opBearer)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"enabled\":true}"))
            .build();
        HttpResponse<String> noCounterResponse = client.send(noCounterRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(409, noCounterResponse.statusCode(),
            "PUT without X-React-Counter should return 409, body: " + noCounterResponse.body());
        JsonObject noCounterBody = JsonParser.parseString(noCounterResponse.body()).getAsJsonObject();
        assertTrue(noCounterBody.has("error"), "409 body must have 'error' object");
        assertTrue(noCounterBody.getAsJsonObject("error").has("message"), "error must have 'message'");

        HttpRequest replayRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/features/feature-a"))
            .header("Authorization", "Bearer " + opBearer)
            .header("X-React-Counter", String.valueOf(c1))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"enabled\":true}"))
            .build();
        HttpResponse<String> replayResponse = client.send(replayRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(409, replayResponse.statusCode(),
            "Replayed counter should return 409, body: " + replayResponse.body());

        long c2 = opCounter.incrementAndGet();
        HttpRequest configRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/features/feature-a/config"))
            .header("Authorization", "Bearer " + opBearer)
            .header("X-React-Counter", String.valueOf(c2))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"budgetMs\":40}"))
            .build();
        HttpResponse<String> configResponse = client.send(configRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, configResponse.statusCode(),
            "PUT /api/v1/features/{id}/config with op token should return 200, body: " + configResponse.body());
        assertTrue(fakeFeatureBackend.setKnobsCalled, "setKnobs should have been called");
        assertEquals("feature-a", fakeFeatureBackend.lastSetKnobsId);
        assertNotNull(fakeFeatureBackend.lastSetKnobsMap, "knobs map should not be null");
        assertTrue(fakeFeatureBackend.lastSetKnobsMap.containsKey("budgetMs"), "knobs map should contain budgetMs");
    }

    @Test
    void tweaksRoutesHappyPath(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord opRecord = new TokenRecord("tok-tw-op", "op-device", 2000L, Set.of("read", "op:execute"), "operator");
        TokenRecord readRecord = new TokenRecord("tok-tw-read", "read-device", 2000L, Set.of("read"), "viewer");
        String opBearer = PairingToken.mint(secret, opRecord.id(), opRecord.label(), opRecord.issuedAt(), opRecord.scopes());
        String readBearer = PairingToken.mint(secret, readRecord.id(), readRecord.label(), readRecord.issuedAt(), readRecord.scopes());
        File tokensFile = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokensFile);
        store.add(opRecord);
        store.add(readRecord);
        store.save(tokensFile);

        FakeControlBackend fakeFeatureBackend = new FakeControlBackend("feature");
        FakeControlBackend fakeTweakBackend = new FakeControlBackend("tweak");

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
        controller.setFeatureControlBackend(fakeFeatureBackend);
        controller.setTweakControlBackend(fakeTweakBackend);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest listRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/tweaks"))
            .header("Authorization", "Bearer " + readBearer)
            .GET()
            .build();
        HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode(),
            "GET /api/v1/tweaks with read token should return 200, body: " + listResponse.body());
        JsonObject listRoot = JsonParser.parseString(listResponse.body()).getAsJsonObject();
        JsonArray listData = listRoot.getAsJsonArray("data");
        assertNotNull(listData, "Response should contain 'data' array");
        assertEquals(fakeTweakBackend.list().size(), listData.size(),
            "data array length should match fake backend item count");

        HttpRequest toggleRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/tweaks/tweak-a"))
            .header("Authorization", "Bearer " + opBearer)
            .header("X-React-Counter", "1")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"enabled\":true}"))
            .build();
        HttpResponse<String> toggleResponse = client.send(toggleRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, toggleResponse.statusCode(),
            "PUT /api/v1/tweaks/{id} with op token should return 200, body: " + toggleResponse.body());
        assertTrue(fakeTweakBackend.setEnabledCalled, "setEnabled should have been called on tweak backend");
        assertEquals("tweak-a", fakeTweakBackend.lastSetEnabledId);
        assertTrue(fakeTweakBackend.lastSetEnabledValue);
    }

    private static final class FakeControlBackend implements ControlBackend {

        private final String prefix;
        private final List<ControlItemDto> items = new ArrayList<>();
        boolean setEnabledCalled = false;
        String lastSetEnabledId;
        boolean lastSetEnabledValue;
        boolean setKnobsCalled = false;
        String lastSetKnobsId;
        Map<String, Object> lastSetKnobsMap;

        FakeControlBackend(String prefix) {
            this.prefix = prefix;
            ControlItemDto a = new ControlItemDto();
            a.id = prefix + "-a";
            a.name = prefix + " A";
            a.category = "test";
            a.enabled = true;
            a.description = "Test " + prefix + " A";
            a.knobs = new art.arcane.react.api.web.dto.KnobDto[0];

            ControlItemDto b = new ControlItemDto();
            b.id = prefix + "-b";
            b.name = prefix + " B";
            b.category = "test";
            b.enabled = false;
            b.description = "Test " + prefix + " B";
            b.knobs = new art.arcane.react.api.web.dto.KnobDto[0];

            items.add(a);
            items.add(b);
        }

        @Override
        public List<ControlItemDto> list() {
            return items;
        }

        @Override
        public ControlItemDto get(String id) {
            for (ControlItemDto item : items) {
                if (item.id.equals(id)) {
                    return item;
                }
            }
            return null;
        }

        @Override
        public ControlItemDto setEnabled(String id, boolean enabled) {
            setEnabledCalled = true;
            lastSetEnabledId = id;
            lastSetEnabledValue = enabled;
            ControlItemDto item = get(id);
            if (item == null) {
                return null;
            }
            item.enabled = enabled;
            return item;
        }

        @Override
        public ControlItemDto setKnobs(String id, Map<String, Object> knobs) {
            setKnobsCalled = true;
            lastSetKnobsId = id;
            lastSetKnobsMap = new HashMap<>(knobs);
            return get(id);
        }
    }
}
