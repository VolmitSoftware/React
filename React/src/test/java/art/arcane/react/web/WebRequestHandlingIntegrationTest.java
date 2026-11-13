package art.arcane.react.web;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.ControlBackend;
import art.arcane.react.api.web.ControlSerializer;
import art.arcane.react.api.web.RegistryControlBackend;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.dto.ControlItemDto;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.controller.PluginApiPackController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.react.util.project.registry.Registered;
import art.arcane.react.util.project.registry.Registry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebRequestHandlingIntegrationTest {

    private static final String PACK_ID = "sample-pack";

    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicLong counter = new AtomicLong(0);
    private final List<LogRecord> logRecords = new CopyOnWriteArrayList<>();
    private final Logger reactLogger = Logger.getLogger("React");
    private final Handler logCapture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logRecords.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    private WebController controller;
    private React previousInstance;
    private Level previousLevel;
    private String bearer;
    private int port;

    @BeforeEach
    void captureLogs() {
        previousInstance = React.instance;
        previousLevel = reactLogger.getLevel();
        React.instance = null;
        reactLogger.setLevel(Level.ALL);
        reactLogger.addHandler(logCapture);
    }

    @AfterEach
    void cleanup() {
        try {
            if (controller != null) {
                controller.stop();
                controller = null;
            }
        } finally {
            reactLogger.removeHandler(logCapture);
            reactLogger.setLevel(previousLevel);
            React.instance = previousInstance;
        }
    }

    @Test
    void shouldAcceptPackValidationWhenCounterIsFresh(@TempDir File dataFolder) throws Exception {
        start(dataFolder, new ThrowingControlBackend());

        HttpResponse<String> response = send("POST", "/api/v1/plugin-api-packs/validate",
            "{\"content\":\"id = 'sample-pack'\"}", counter.incrementAndGet());

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(data(response).get("valid").getAsBoolean());
    }

    @Test
    void shouldRejectPackValidationWhenCounterIsReplayed(@TempDir File dataFolder) throws Exception {
        start(dataFolder, new ThrowingControlBackend());
        long replayed = counter.incrementAndGet();
        String body = "{\"content\":\"id = 'sample-pack'\"}";

        assertEquals(200, send("POST", "/api/v1/plugin-api-packs/validate", body, replayed).statusCode());
        assertEquals(409, send("POST", "/api/v1/plugin-api-packs/validate", body, replayed).statusCode());
    }

    @Test
    void shouldCheckCounterOncePerRequestWhenPackRoutesMutate(@TempDir File dataFolder) throws Exception {
        start(dataFolder, new ThrowingControlBackend());

        HttpResponse<String> put = send("PUT", "/api/v1/plugin-api-packs/" + PACK_ID,
            "{\"content\":\"id = 'sample-pack'\"}", counter.incrementAndGet());
        HttpResponse<String> delete = send("DELETE", "/api/v1/plugin-api-packs/" + PACK_ID,
            null, counter.incrementAndGet());

        assertEquals(200, put.statusCode(), put.body());
        assertEquals(200, delete.statusCode(), delete.body());
    }

    @Test
    void shouldReturnBadRequestWhenToggleBodyIsNotValidJson(@TempDir File dataFolder) throws Exception {
        start(dataFolder, new ThrowingControlBackend());

        assertBadRequest(send("PUT", "/api/v1/features/feature-a", "{bad json", counter.incrementAndGet()));
        assertBadRequest(send("PUT", "/api/v1/features/feature-a", "", counter.incrementAndGet()));
        assertBadRequest(send("PUT", "/api/v1/features/feature-a", "[true]", counter.incrementAndGet()));
        assertBadRequest(send("PUT", "/api/v1/features/feature-a",
            "{\"enabled\":\"sometimes\"}", counter.incrementAndGet()));
        assertBadRequest(send("PUT", "/api/v1/features/feature-a/config", "[1,2]", counter.incrementAndGet()));
        assertBadRequest(send("PUT", "/api/v1/config", "{bad json", counter.incrementAndGet()));
        assertBadRequest(send("POST", "/api/v1/console/execute", "[\"say hi\"]", counter.incrementAndGet()));
        assertNoUnhandledWarning();
    }

    @Test
    void shouldReturnBadRequestWhenKnobIsUnknown(@TempDir File dataFolder) throws Exception {
        TestControl control = new TestControl();
        start(dataFolder, new RegistryControlBackend<TestControl>(
            "feature",
            () -> List.of(control),
            id -> control.getId().equals(id) ? control : null,
            item -> true,
            new ControlSerializer(),
            (path, value) -> true
        ));

        HttpResponse<String> response = send("PUT", "/api/v1/features/test-control/config",
            "{\"missing\":5}", counter.incrementAndGet());

        assertBadRequest(response);
        assertTrue(errorMessage(response).contains("missing"), response.body());
        assertNoUnhandledWarning();
    }

    @Test
    void shouldReturnServerErrorWhenBackendFails(@TempDir File dataFolder) throws Exception {
        start(dataFolder, new ThrowingControlBackend());

        HttpResponse<String> response = send("PUT", "/api/v1/features/feature-a",
            "{\"enabled\":true}", counter.incrementAndGet());

        assertEquals(500, response.statusCode(), response.body());
        assertEquals("Internal server error", errorMessage(response));
        assertTrue(logRecords.stream().anyMatch(WebRequestHandlingIntegrationTest::isUnhandledWarning),
            "A server fault should still be logged");
    }

    private void start(File dataFolder, ControlBackend featureBackend) throws Exception {
        bearer = WebTestTokens.issue(dataFolder, "admin-device", "admin");

        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);

        controller = new WebController() {
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
        controller.setConfig(config);
        controller.setDataFolder(dataFolder);
        controller.setSampleController(sampleController);
        controller.setFeatureControlBackend(featureBackend);
        controller.setPluginApiPackController(packController());
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);
        port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);
    }

    private static PluginApiPackController packController() throws Exception {
        PluginApiPackRuntime.PackStatus status = new PluginApiPackRuntime.PackStatus(
            PACK_ID, "1.0.0", "Sample", List.of(), "Target", "", List.of(), true, false,
            PluginApiPackRuntime.PackState.LOADING, "reload-queued", PACK_ID + ".toml", "", List.of());
        PluginApiPackController packs = mock(PluginApiPackController.class);
        when(packs.validate(anyString()))
            .thenReturn(new PluginApiPackController.ValidationResult(true, PACK_ID, 0, "valid"));
        when(packs.install(anyString(), anyString())).thenReturn(status);
        when(packs.remove(anyString())).thenReturn(true);
        when(packs.statuses()).thenReturn(List.of());
        when(packs.validationErrors()).thenReturn(Map.of());
        return packs;
    }

    private HttpResponse<String> send(String method, String path, String body, long counterValue) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Authorization", "Bearer " + bearer)
            .header("Content-Type", "application/json")
            .header("X-React-Counter", Long.toString(counterValue))
            .method(method, publisher)
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertBadRequest(HttpResponse<String> response) {
        assertEquals(400, response.statusCode(), response.body());
        assertFalse(errorMessage(response).isBlank(), response.body());
    }

    private void assertNoUnhandledWarning() {
        assertFalse(logRecords.stream().anyMatch(WebRequestHandlingIntegrationTest::isUnhandledWarning),
            "Client errors must not be logged as unhandled web exceptions");
    }

    private static boolean isUnhandledWarning(LogRecord record) {
        return record.getMessage() != null && record.getMessage().contains("Unhandled web exception");
    }

    private static JsonObject data(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
    }

    private static String errorMessage(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject()
            .getAsJsonObject("error").get("message").getAsString();
    }

    private static final class ThrowingControlBackend implements ControlBackend {

        @Override
        public List<ControlItemDto> list() {
            return List.of();
        }

        @Override
        public ControlItemDto get(String id) {
            return null;
        }

        @Override
        public ControlItemDto setEnabled(String id, boolean enabled) {
            throw new IllegalStateException("backend unavailable");
        }

        @Override
        public ControlItemDto setKnobs(String id, Map<String, Object> knobs) {
            throw new IllegalStateException("backend unavailable");
        }
    }

    private static final class TestControl implements Registered {
        @ConfigDoc(value = "First value.")
        private int first = 1;

        @Override
        public String getId() {
            return "test-control";
        }
    }
}
