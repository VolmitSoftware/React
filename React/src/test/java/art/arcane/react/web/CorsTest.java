package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.util.project.registry.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CorsTest {

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
                dto.serverName = "CorsTest";
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

    private int startWith(File dataFolder, List<String> origins) throws Exception {
        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(false);
        config.setAllowedOrigins(origins);
        controller = buildController(config, dataFolder);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);
        return controller.getBoundPort();
    }

    private static HttpResponse<String> get(int port, String path, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (origin != null) {
            request.header("Origin", origin);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void anAllowedOriginIsAnsweredWithItsOwnOrigin(@TempDir File dataFolder) throws Exception {
        int port = startWith(dataFolder, List.of("http://localhost:8080"));
        HttpResponse<String> response = get(port, "/api/v1/metrics", "http://localhost:8080");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("http://localhost:8080", response.headers().firstValue("access-control-allow-origin").orElse(null));
        String exposed = response.headers().firstValue("access-control-expose-headers").orElse("");
        assertTrue(exposed.toLowerCase(Locale.ROOT).contains("retry-after"), "a page must be able to read Retry-After, got [" + exposed + "]");
    }

    @Test
    void anOriginOutsideTheListIsRefusedWithAReadableBodyNamingTheSetting(@TempDir File dataFolder) throws Exception {
        int port = startWith(dataFolder, List.of("http://localhost:8080"));
        HttpResponse<String> response = get(port, "/api/v1/metrics", "https://evil.example");
        assertEquals(403, response.statusCode());
        assertEquals("https://evil.example", response.headers().firstValue("access-control-allow-origin").orElse(null),
            "the refusal must be readable by the page");
        JsonObject error = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("error");
        assertEquals("origin_not_allowed", error.get("code").getAsString());
        assertEquals("allowedOrigins", error.get("configKey").getAsString());
        assertEquals("plugins/React/web.toml", error.get("configFile").getAsString());
        assertEquals("https://evil.example", error.get("origin").getAsString());
        assertNotNull(error.get("message"));
    }

    @Test
    void theDefaultListHoldsOnlyTheHostedApp(@TempDir File dataFolder) throws Exception {
        int port = startWith(dataFolder, WebConfiguration.DEFAULT_ALLOWED_ORIGINS);
        assertEquals(200, get(port, "/api/v1/ping", "https://react.volmitsoftware.com").statusCode());
        assertEquals(403, get(port, "/api/v1/ping", "http://localhost:8080").statusCode());
        assertEquals(200, get(port, "/api/v1/ping", null).statusCode(), "a request without Origin is not a browser");
    }

    @Test
    void everyPreflightSucceedsSoThePageCanReadTheAnswer(@TempDir File dataFolder) throws Exception {
        int port = startWith(dataFolder, List.of("http://localhost:8080"));
        for (String origin : List.of("http://localhost:8080", "https://elsewhere.example")) {
            HttpRequest preflight = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/pair"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type")
                .header("Access-Control-Request-Private-Network", "true")
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(preflight, HttpResponse.BodyHandlers.ofString());
            assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, "preflight from " + origin + ": " + response.statusCode());
            assertEquals(origin, response.headers().firstValue("access-control-allow-origin").orElse(null));
            String headers = response.headers().firstValue("access-control-allow-headers").orElse("").toLowerCase(Locale.ROOT);
            assertTrue(headers.contains("authorization") && headers.contains("x-react-counter"), headers);
            assertEquals("true", response.headers().firstValue("access-control-allow-private-network").orElse(null));
        }
    }
}
