package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebSecret;
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
                dto.version = "test";
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

    @Test
    void anyHostGetResponseIncludesAllowOriginHeader(@TempDir File dataFolder) throws Exception {
        WebSecret.load(dataFolder);

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(false);

        controller = buildController(config, dataFolder);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/metrics"))
            .header("Origin", "http://localhost:8080")
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String acao = response.headers().firstValue("access-control-allow-origin").orElse(null);
        assertNotNull(acao, "Expected Access-Control-Allow-Origin header in response but it was absent. Headers: " + response.headers().map());
    }

    @Test
    void anyHostPreflightWithAuthorizationSucceeds(@TempDir File dataFolder) throws Exception {
        WebSecret.load(dataFolder);

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(false);

        controller = buildController(config, dataFolder);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest preflight = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/metrics"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", "http://localhost:8080")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Authorization")
            .build();
        HttpResponse<String> response = client.send(preflight, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        assertTrue(status >= 200 && status < 300,
            "OPTIONS preflight should return 2xx but returned " + status + ". Headers: " + response.headers().map());

        String acah = response.headers().firstValue("access-control-allow-headers").orElse("");
        assertTrue(acah.toLowerCase(Locale.ROOT).contains("authorization"),
            "Access-Control-Allow-Headers should include authorization but was: [" + acah + "]. Headers: " + response.headers().map());
    }

    @Test
    void specificOriginGetResponseReflectsConfiguredOrigin(@TempDir File dataFolder) throws Exception {
        WebSecret.load(dataFolder);

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(false);
        config.setCorsOrigins(List.of("http://localhost:8080"));

        controller = buildController(config, dataFolder);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/metrics"))
            .header("Origin", "http://localhost:8080")
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String acao = response.headers().firstValue("access-control-allow-origin").orElse(null);
        assertNotNull(acao, "Expected Access-Control-Allow-Origin header but it was absent. Headers: " + response.headers().map());
        assertTrue(acao.contains("localhost:8080") || acao.equals("*"),
            "Access-Control-Allow-Origin should reflect configured origin. Got: " + acao);
    }
}
