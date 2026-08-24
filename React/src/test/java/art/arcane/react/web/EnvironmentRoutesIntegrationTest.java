package art.arcane.react.web;

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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EnvironmentRoutesIntegrationTest {

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
    void cleanup() {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
    }

    @Test
    void environmentEndpointRequiresTokenWhenReadGuardEnabled(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord readRecord = new TokenRecord("tok-env-read", "read-device", 1000L, Set.of("read"), "viewer");
        String readBearer = PairingToken.mint(secret, readRecord.id(), readRecord.label(), readRecord.issuedAt(), readRecord.scopes());
        File tokensFile = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokensFile);
        store.add(readRecord);
        store.save(tokensFile);

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
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest noAuthRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/environment"))
            .GET()
            .build();
        HttpResponse<String> noAuthResponse = client.send(noAuthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noAuthResponse.statusCode(),
            "GET /api/v1/environment without token should return 401, body: " + noAuthResponse.body());

        HttpRequest authRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/environment"))
            .header("Authorization", "Bearer " + readBearer)
            .GET()
            .build();
        HttpResponse<String> authResponse = client.send(authRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, authResponse.statusCode(),
            "GET /api/v1/environment with read token should return 200, body: " + authResponse.body());

        JsonObject root = JsonParser.parseString(authResponse.body()).getAsJsonObject();
        JsonObject data = root.getAsJsonObject("data");
        assertNotNull(data, "Response must have 'data' object");
        assertNotNull(data.getAsJsonObject("cpu"), "data.cpu must be present");
        assertNotNull(data.getAsJsonObject("memory"), "data.memory must be present");
        assertNotNull(data.getAsJsonObject("jvm"), "data.jvm must be present");
        assertNotNull(data.getAsJsonObject("server"), "data.server must be present");
        assertNotNull(data.getAsJsonArray("disks"), "data.disks must be present");
        assertNotNull(data.getAsJsonArray("mounts"), "data.mounts must be present");
        assertNotNull(data.getAsJsonArray("network"), "data.network must be present");
        assertEquals("9.9.9-test", data.getAsJsonObject("server").get("version").getAsString(),
            "data.server.version must match injected identity");
    }
}
