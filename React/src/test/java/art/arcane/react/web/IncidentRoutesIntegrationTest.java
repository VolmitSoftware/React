package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebSecret;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.core.incident.IncidentEvidence;
import art.arcane.react.core.incident.IncidentRecord;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IncidentRoutesIntegrationTest {

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
    void incidentRouteAuthAndHappyPath(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord readRecord = new TokenRecord("tok-inc-read", "read-device", 3000L, Set.of("read"), "viewer");
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

        controller = buildController(config, dataFolder, sampleController);
        IncidentEvidence evidence = new IncidentEvidence(
            "gc-time-percent",
            "GC Time",
            true,
            12D,
            "12%",
            0.43D,
            0.10D,
            4.3D,
            2D,
            25D
        );
        controller.setIncidentSnapshotSupplier(() -> new SamplerIncidentScore.IncidentScoreSnapshot(
            1234L,
            58D,
            true,
            List.of(evidence)
        ));
        controller.setIncidentStateSupplier(() -> "ACTIVE");
        controller.setIncidentRecordsSupplier(limit -> List.of(new IncidentRecord(
            "event-id",
            "incident-id",
            "SERVER_PRESSURE",
            "STARTED",
            "WARNING",
            1234L,
            1234L,
            "incident-mode",
            "Incident mode engaged",
            "Guardrails active",
            "GC time was elevated",
            null,
            List.of(evidence),
            List.of(),
            Map.of()
        )));
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest noTokenRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/incidents"))
            .GET()
            .build();
        HttpResponse<String> noTokenResponse = client.send(noTokenRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noTokenResponse.statusCode(),
            "GET /api/v1/incidents without token should return 401, body: " + noTokenResponse.body());

        HttpRequest withTokenRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/incidents"))
            .header("Authorization", "Bearer " + readBearer)
            .GET()
            .build();
        HttpResponse<String> withTokenResponse = client.send(withTokenRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, withTokenResponse.statusCode(),
            "GET /api/v1/incidents with read token should return 200, body: " + withTokenResponse.body());

        JsonObject root = JsonParser.parseString(withTokenResponse.body()).getAsJsonObject();
        JsonObject data = root.getAsJsonObject("data");
        assertNotNull(data, "Response should contain 'data' object");
        assertEquals(58.0, data.get("score").getAsDouble(), 1e-9);
        assertEquals("ACTIVE", data.get("state").getAsString());
        assertTrue(data.get("scoreAvailable").getAsBoolean());
        assertEquals(1234L, data.get("sampledAtMs").getAsLong());
        JsonArray incidents = data.getAsJsonArray("incidents");
        assertNotNull(incidents, "data should contain 'incidents' array");
        assertEquals(1, incidents.size());
        assertEquals("GC time was elevated", incidents.get(0).getAsJsonObject().get("cause").getAsString());
        JsonArray contributors = data.getAsJsonArray("contributors");
        assertNotNull(contributors, "data should contain 'contributors' array");
        assertEquals(1, contributors.size());
        JsonObject contrib = contributors.get(0).getAsJsonObject();
        assertEquals("gc-time-percent", contrib.get("id").getAsString());
        assertEquals("GC Time", contrib.get("label").getAsString());
        assertTrue(contrib.get("available").getAsBoolean());
        assertEquals(0.10, contrib.get("weight").getAsDouble(), 1e-9);
        assertEquals(12.0, contrib.get("value").getAsDouble(), 1e-9);
        assertEquals(4.3, contrib.get("scorePoints").getAsDouble(), 1e-9);
    }
}
