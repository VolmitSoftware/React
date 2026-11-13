package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.WebConfiguration;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WhoamiRoutesIntegrationTest {

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
    void whoamiReturnsRoleAndScopes(@TempDir File dataFolder) throws Exception {


        String viewerBearer = WebTestTokens.issue(dataFolder, "viewer-device", "viewer");
        String operatorBearer = WebTestTokens.issue(dataFolder, "op-device", "operator");
        String adminBearer = WebTestTokens.issue(dataFolder, "admin-device", "admin");
        String customBearer = WebTestTokens.issue(dataFolder, "custom-device", List.of("read", "console:read"));


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
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();
        String url = "http://127.0.0.1:" + port + "/api/v1/whoami";

        HttpResponse<String> viewerResponse = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + viewerBearer)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, viewerResponse.statusCode(),
            "Viewer bearer should return 200, body: " + viewerResponse.body());
        JsonObject viewerRoot = JsonParser.parseString(viewerResponse.body()).getAsJsonObject();
        assertNotNull(viewerRoot.getAsJsonObject("data"), "Response must have 'data' object");
        JsonObject viewerData = viewerRoot.getAsJsonObject("data");
        assertEquals("viewer", viewerData.get("role").getAsString(), "role must be viewer");
        JsonArray viewerScopes = viewerData.getAsJsonArray("scopes");
        assertNotNull(viewerScopes, "scopes must be present");
        assertEquals(1, viewerScopes.size(), "viewer should have exactly 1 scope");
        assertEquals("read", viewerScopes.get(0).getAsString(), "viewer scope must be read");

        HttpResponse<String> operatorResponse = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + operatorBearer)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, operatorResponse.statusCode(),
            "Operator bearer should return 200, body: " + operatorResponse.body());
        JsonObject operatorData = JsonParser.parseString(operatorResponse.body()).getAsJsonObject().getAsJsonObject("data");
        assertNotNull(operatorData, "operator response must have 'data' object");
        assertEquals("operator", operatorData.get("role").getAsString(), "role must be operator");
        JsonArray operatorScopes = operatorData.getAsJsonArray("scopes");
        assertNotNull(operatorScopes, "operator scopes must be present");
        List<String> operatorScopeList = new ArrayList<>();
        operatorScopes.forEach(e -> operatorScopeList.add(e.getAsString()));
        assertTrue(operatorScopeList.contains("read"), "operator scopes must contain read");
        assertTrue(operatorScopeList.contains("op:execute"), "operator scopes must contain op:execute");
        assertFalse(operatorScopeList.contains("admin"), "operator scopes must NOT contain admin");

        HttpResponse<String> adminResponse = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + adminBearer)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, adminResponse.statusCode(),
            "Admin bearer should return 200, body: " + adminResponse.body());
        JsonObject adminData = JsonParser.parseString(adminResponse.body()).getAsJsonObject().getAsJsonObject("data");
        assertNotNull(adminData, "admin response must have 'data' object");
        assertEquals("admin", adminData.get("role").getAsString(), "role must be admin");
        JsonArray adminScopes = adminData.getAsJsonArray("scopes");
        assertNotNull(adminScopes, "admin scopes must be present");
        List<String> adminScopeList = new ArrayList<>();
        adminScopes.forEach(e -> adminScopeList.add(e.getAsString()));
        assertTrue(adminScopeList.contains("read"), "admin scopes must contain read");
        assertTrue(adminScopeList.contains("op:execute"), "admin scopes must contain op:execute");
        assertTrue(adminScopeList.contains("admin"), "admin scopes must contain admin");
        assertTrue(adminScopeList.contains("console:read"), "admin scopes must contain console:read");
        assertTrue(adminScopeList.contains("console:execute"), "admin scopes must contain console:execute");
        assertEquals(
            List.of("read", "op:execute", "admin", "console:read", "console:execute"),
            adminScopeList,
            "admin scopes come in the documented order"
        );
        assertEquals(WebTestTokens.idOf(adminBearer), adminData.get("tokenId").getAsString());
        assertEquals("admin-device", adminData.get("label").getAsString());
        assertEquals(7L * 24 * 3600, adminData.get("lifetimeSeconds").getAsLong());
        assertTrue(java.time.Instant.parse(adminData.get("expiresAt").getAsString()).isAfter(java.time.Instant.now().plusSeconds(6L * 24 * 3600)));

        HttpResponse<String> customResponse = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + customBearer)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, customResponse.statusCode(), "custom scopes bearer should return 200, body: " + customResponse.body());
        JsonObject customData = JsonParser.parseString(customResponse.body()).getAsJsonObject().getAsJsonObject("data");
        assertEquals("custom", customData.get("role").getAsString(), "scopes that match no role read as custom");
        List<String> customScopeList = new ArrayList<>();
        customData.getAsJsonArray("scopes").forEach(e -> customScopeList.add(e.getAsString()));
        assertEquals(List.of("read", "console:read"), customScopeList);

        HttpResponse<String> noAuthResponse = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(401, noAuthResponse.statusCode(),
            "No Authorization header should return 401, body: " + noAuthResponse.body());
        JsonObject noAuthRoot = JsonParser.parseString(noAuthResponse.body()).getAsJsonObject();
        assertTrue(noAuthRoot.has("error"), "401 body must have 'error' object");
        assertTrue(noAuthRoot.getAsJsonObject("error").has("message"), "error must have 'message'");
        assertEquals("unauthorized", noAuthRoot.getAsJsonObject("error").get("code").getAsString());
    }
}
