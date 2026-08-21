package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
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
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CapabilityRoutesIntegrationTest {

    private WebController controller;

    @AfterEach
    void cleanup() {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
    }

    @Test
    void pingIsUnauthenticatedAndExposesOnlyPublicCapabilities(@TempDir File dataFolder) throws Exception {
        WebSecret.load(dataFolder);
        File tokensFile = new File(dataFolder, "web/tokens.toml");
        TokenStore.fromToml(tokensFile).save(tokensFile);

        WebConfiguration config = new WebConfiguration();
        config.setEnabled(true);
        config.setBindAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(true);
        config.setRelayEnabled(true);
        config.setRelayUrl("");
        controller = buildController(config, dataFolder);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + controller.getBoundPort() + "/api/v1/ping"))
            .GET()
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode(), response.body());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(Set.of("data"), root.keySet());
        JsonObject data = root.getAsJsonObject("data");
        assertEquals(Set.of("protocolVersion", "serverFingerprint", "relayAvailable"), data.keySet());
        assertEquals(2, data.get("protocolVersion").getAsInt());
        assertTrue(data.get("serverFingerprint").getAsString().matches("[0-9a-f]{64}"));
        assertEquals(controller.getIdentity().fingerprint(), data.get("serverFingerprint").getAsString());
        assertFalse(data.get("relayAvailable").getAsBoolean());
    }

    private static WebController buildController(WebConfiguration config, File dataFolder) {
        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());
        Logger logger = Logger.getLogger("react-capability-route-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        WebController c = new WebController() {
            @Override
            protected void executeAsync(Runnable runnable) {
                Thread.ofVirtual().start(runnable);
            }

            @Override
            protected IdentityDto resolveIdentity() {
                IdentityDto dto = new IdentityDto();
                dto.version = "9.9.9-test";
                dto.serverName = "SensitiveName";
                dto.folia = false;
                dto.serverId = "sensitive.example.net:25565";
                return dto;
            }

            @Override
            protected Logger resolveConsoleLogger() {
                return logger;
            }
        };
        c.setConfig(config);
        c.setDataFolder(dataFolder);
        c.setSampleController(sampleController);
        return c;
    }
}
