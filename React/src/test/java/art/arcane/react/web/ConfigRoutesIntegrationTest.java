package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.ConfigApplier;
import art.arcane.react.api.web.ConfigTreeSerializer;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.PresetApplier;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebSecret;
import art.arcane.react.api.web.dto.ConfigNodeDto;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.model.ReactConfiguration;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigRoutesIntegrationTest {

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
    void configRoutesHappyPathAndAuthGuards(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord adminRecord = new TokenRecord("tok-cfg-admin", "admin-device", 1000L, Set.of("read", "op:execute", "admin"), "admin");
        TokenRecord opRecord = new TokenRecord("tok-cfg-op", "op-device", 1000L, Set.of("read", "op:execute"), "operator");
        TokenRecord readRecord = new TokenRecord("tok-cfg-read", "read-device", 1000L, Set.of("read"), "viewer");
        String adminBearer = PairingToken.mint(secret, adminRecord.id(), adminRecord.label(), adminRecord.issuedAt(), adminRecord.scopes());
        String opBearer = PairingToken.mint(secret, opRecord.id(), opRecord.label(), opRecord.issuedAt(), opRecord.scopes());
        String readBearer = PairingToken.mint(secret, readRecord.id(), readRecord.label(), readRecord.issuedAt(), readRecord.scopes());
        File tokensFile = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokensFile);
        store.add(adminRecord);
        store.add(opRecord);
        store.add(readRecord);
        store.save(tokensFile);

        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());

        WebConfiguration config = new WebConfiguration();
        config.setEnabled(true);
        config.setBindAddress("127.0.0.1");
        config.setPort(0);

        RecordingConfigApplier recordingApplier = new RecordingConfigApplier();
        RecordingPresetApplier recordingPresetApplier = new RecordingPresetApplier();
        ConfigTreeSerializer serializer = new ConfigTreeSerializer();

        controller = buildController(config, dataFolder, sampleController);
        controller.setConfigTreeSupplier(() -> serializer.serialize(new ReactConfiguration()));
        controller.setConfigApplier(recordingApplier);
        controller.setPresetApplier(recordingPresetApplier);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();
        AtomicLong adminCounter = new AtomicLong(0);
        AtomicLong opCounter = new AtomicLong(0);
        AtomicLong readCounter = new AtomicLong(0);

        HttpRequest getRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/config"))
            .header("Authorization", "Bearer " + readBearer)
            .GET()
            .build();
        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode(),
            "GET /api/v1/config with read bearer should return 200, body: " + getResponse.body());
        JsonObject getRoot = JsonParser.parseString(getResponse.body()).getAsJsonObject();
        JsonObject getData = getRoot.getAsJsonObject("data");
        assertNotNull(getData, "Response should contain 'data' object");
        JsonArray sections = getData.getAsJsonArray("sections");
        assertNotNull(sections, "data should have 'sections' array");
        assertFalse(sections.isEmpty(), "sections should be non-empty");

        boolean foundVerbose = false;
        for (JsonElement sectionEl : sections) {
            JsonObject sectionObj = sectionEl.getAsJsonObject();
            JsonArray nodes = sectionObj.getAsJsonArray("nodes");
            if (nodes != null) {
                for (JsonElement nodeEl : nodes) {
                    JsonObject nodeObj = nodeEl.getAsJsonObject();
                    if ("main.verbose".equals(nodeObj.get("key").getAsString())) {
                        foundVerbose = true;
                    }
                }
            }
        }
        assertTrue(foundVerbose, "Flattened nodes should include key main.verbose");

        String putBody = "{\"main.verbose\":true}";

        long a1 = adminCounter.incrementAndGet();
        HttpRequest putAdminRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/config"))
            .header("Authorization", "Bearer " + adminBearer)
            .header("X-React-Counter", String.valueOf(a1))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(putBody))
            .build();
        HttpResponse<String> putAdminResponse = client.send(putAdminRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, putAdminResponse.statusCode(),
            "PUT /api/v1/config with admin bearer should return 200, body: " + putAdminResponse.body());
        JsonObject putAdminRoot = JsonParser.parseString(putAdminResponse.body()).getAsJsonObject();
        assertNotNull(putAdminRoot.getAsJsonObject("data"), "PUT response should have 'data' object");
        assertNotNull(putAdminRoot.getAsJsonObject("data").getAsJsonArray("sections"), "PUT response data should have sections");
        assertTrue(recordingApplier.invocations.contains("main.verbose"),
            "Recording applier should have seen main.verbose path");

        long o1 = opCounter.incrementAndGet();
        HttpRequest putOpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/config"))
            .header("Authorization", "Bearer " + opBearer)
            .header("X-React-Counter", String.valueOf(o1))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(putBody))
            .build();
        HttpResponse<String> putOpResponse = client.send(putOpRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, putOpResponse.statusCode(),
            "PUT /api/v1/config with operator bearer should return 403, body: " + putOpResponse.body());
        JsonObject putOpForbiddenBody = JsonParser.parseString(putOpResponse.body()).getAsJsonObject();
        assertTrue(putOpForbiddenBody.has("error"), "403 body must have 'error' object");
        assertTrue(putOpForbiddenBody.getAsJsonObject("error").has("message"), "error must have 'message'");

        long r1 = readCounter.incrementAndGet();
        HttpRequest putReadRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/config"))
            .header("Authorization", "Bearer " + readBearer)
            .header("X-React-Counter", String.valueOf(r1))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(putBody))
            .build();
        HttpResponse<String> putReadResponse = client.send(putReadRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, putReadResponse.statusCode(),
            "PUT /api/v1/config with read-only bearer should return 403, body: " + putReadResponse.body());
        JsonObject forbiddenBody = JsonParser.parseString(putReadResponse.body()).getAsJsonObject();
        assertTrue(forbiddenBody.has("error"), "403 body must have 'error' object");
        assertTrue(forbiddenBody.getAsJsonObject("error").has("message"), "error must have 'message'");

        HttpRequest putNoCounterRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/config"))
            .header("Authorization", "Bearer " + adminBearer)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(putBody))
            .build();
        HttpResponse<String> putNoCounterResponse = client.send(putNoCounterRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(409, putNoCounterResponse.statusCode(),
            "PUT /api/v1/config without X-React-Counter should return 409, body: " + putNoCounterResponse.body());
        JsonObject noCounterBody = JsonParser.parseString(putNoCounterResponse.body()).getAsJsonObject();
        assertTrue(noCounterBody.has("error"), "409 body must have 'error' object");

        long a2 = adminCounter.incrementAndGet();
        HttpRequest presetRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/config/preset/off"))
            .header("Authorization", "Bearer " + adminBearer)
            .header("X-React-Counter", String.valueOf(a2))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> presetResponse = client.send(presetRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, presetResponse.statusCode(),
            "POST /api/v1/config/preset/off with admin bearer should return 200, body: " + presetResponse.body());
        JsonObject presetRoot = JsonParser.parseString(presetResponse.body()).getAsJsonObject();
        assertNotNull(presetRoot.getAsJsonObject("data"), "preset response should have 'data' object");
        assertNotNull(presetRoot.getAsJsonObject("data").getAsJsonArray("sections"), "preset response data should have sections");
        assertTrue(recordingPresetApplier.invocations.contains("off"),
            "RecordingPresetApplier should have seen 'off'");

        long o2 = opCounter.incrementAndGet();
        HttpRequest presetOpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/config/preset/off"))
            .header("Authorization", "Bearer " + opBearer)
            .header("X-React-Counter", String.valueOf(o2))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> presetOpResponse = client.send(presetOpRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, presetOpResponse.statusCode(),
            "POST /api/v1/config/preset/off with operator bearer should return 403, body: " + presetOpResponse.body());
        JsonObject presetOpForbiddenBody = JsonParser.parseString(presetOpResponse.body()).getAsJsonObject();
        assertTrue(presetOpForbiddenBody.has("error"), "403 body must have 'error' object");
        assertTrue(presetOpForbiddenBody.getAsJsonObject("error").has("message"), "error must have 'message'");

        long a3 = adminCounter.incrementAndGet();
        HttpRequest bogusPresetRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/config/preset/bogus"))
            .header("Authorization", "Bearer " + adminBearer)
            .header("X-React-Counter", String.valueOf(a3))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> bogusPresetResponse = client.send(bogusPresetRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, bogusPresetResponse.statusCode(),
            "POST /api/v1/config/preset/bogus with admin bearer should return 400, body: " + bogusPresetResponse.body());
        JsonObject bogusBody = JsonParser.parseString(bogusPresetResponse.body()).getAsJsonObject();
        assertTrue(bogusBody.has("error"), "400 body must have 'error' object");
        assertTrue(bogusBody.getAsJsonObject("error").has("message"), "error must have 'message'");
    }

    private static final class RecordingConfigApplier implements ConfigApplier {
        final List<String> invocations = new ArrayList<>();

        @Override
        public boolean apply(String path, Object value) {
            invocations.add(path);
            return true;
        }
    }

    private static final class RecordingPresetApplier implements PresetApplier {
        final List<String> invocations = new ArrayList<>();

        @Override
        public int apply(String name) {
            invocations.add(name);
            if ("off".equals(name) || "light".equals(name) || "balanced".equals(name) || "high".equals(name)) {
                return 5;
            }
            return -1;
        }
    }
}
