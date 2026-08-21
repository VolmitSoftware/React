package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.ConsoleCommandDispatcher;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConsoleRoutesIntegrationTest {

    private WebController controller;

    @AfterEach
    void cleanup() {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
    }

    @Test
    void consoleExecuteRequiresAdminScopeAndMonotonicCounter(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord adminRecord = new TokenRecord(
            "tok-console-admin-route",
            "admin-device",
            1000L,
            Set.of("read", "op:execute", "admin"),
            "admin"
        );
        TokenRecord operatorRecord = new TokenRecord(
            "tok-console-op-route",
            "operator-device",
            1000L,
            Set.of("read", "op:execute"),
            "operator"
        );
        String adminBearer = bearer(secret, adminRecord);
        String operatorBearer = bearer(secret, operatorRecord);
        File tokensFile = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokensFile);
        store.add(adminRecord);
        store.add(operatorRecord);
        store.save(tokensFile);

        WebConfiguration config = new WebConfiguration();
        config.setEnabled(true);
        config.setBindAddress("127.0.0.1");
        config.setPort(0);
        AtomicReference<String> dispatchedCommand = new AtomicReference<>();
        ConsoleCommandDispatcher dispatcher = command -> {
            dispatchedCommand.set(command);
            return true;
        };
        controller = buildController(config, dataFolder, dispatcher);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        String url = "http://127.0.0.1:" + controller.getBoundPort() + "/api/v1/console/execute";
        HttpClient client = HttpClient.newHttpClient();
        String body = "{\"command\":\"/say routed\"}";

        HttpResponse<String> unauthenticated = send(client, url, body, null, null);
        assertEquals(401, unauthenticated.statusCode(), unauthenticated.body());

        HttpResponse<String> operator = send(client, url, body, operatorBearer, "1");
        assertEquals(403, operator.statusCode(), operator.body());

        HttpResponse<String> missingCounter = send(client, url, body, adminBearer, null);
        assertEquals(409, missingCounter.statusCode(), missingCounter.body());

        HttpResponse<String> accepted = send(client, url, body, adminBearer, "1");
        assertEquals(202, accepted.statusCode(), accepted.body());
        JsonObject data = JsonParser.parseString(accepted.body()).getAsJsonObject().getAsJsonObject("data");
        assertTrue(data.get("dispatched").getAsBoolean());
        assertEquals("say routed", dispatchedCommand.get());

        HttpResponse<String> replayed = send(client, url, body, adminBearer, "1");
        assertEquals(409, replayed.statusCode(), replayed.body());
    }

    private static WebController buildController(
        WebConfiguration config,
        File dataFolder,
        ConsoleCommandDispatcher dispatcher
    ) {
        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());
        Logger logger = Logger.getLogger("react-console-route-test-" + System.nanoTime());
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
                dto.serverName = "TestBrand";
                dto.folia = false;
                dto.serverId = "127.0.0.1:0";
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
        c.setConsoleCommandDispatcher(dispatcher);
        return c;
    }

    private static HttpResponse<String> send(
        HttpClient client,
        String url,
        String body,
        String bearer,
        String counter
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json");
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (counter != null) {
            request.header("X-React-Counter", counter);
        }
        return client.send(
            request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private static String bearer(byte[] secret, TokenRecord record) {
        return PairingToken.mint(secret, record.id(), record.label(), record.issuedAt(), record.scopes());
    }
}
