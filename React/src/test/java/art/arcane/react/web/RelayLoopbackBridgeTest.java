package art.arcane.react.web;

import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WebError;
import art.arcane.react.api.web.pairing.WebTokens;
import art.arcane.react.api.web.dto.ErrorEnvelope;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.api.web.relay.RelayLoopbackBridge;
import art.arcane.react.api.web.relay.RelayRequestBridge;
import art.arcane.react.api.web.resource.IdentityResource;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.http.HttpResponseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Clock;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelayLoopbackBridgeTest {

    private Javalin javalin;

    @AfterEach
    void cleanup() {
        if (javalin != null) {
            javalin.stop();
            javalin = null;
        }
    }

    private int startMinimalJavalin(WebAuth auth, IdentityResource identityResource) {
        javalin = Javalin.create();
        javalin.exception(HttpResponseException.class, (e, ctx) -> {
            String msg = e.getMessage() == null ? "Error" : e.getMessage();
            ctx.status(e.getStatus()).json(new ErrorEnvelope(new ErrorEnvelope.Message(WebError.codeFor(e.getStatus()), msg)));
        });
        javalin.exception(WebError.class, (e, ctx) -> ctx.status(e.status()).json(e.body()));
        javalin.before("/api/v1/identity", auth);
        javalin.get("/api/v1/identity", identityResource::get);
        javalin.start("127.0.0.1", 0);
        return javalin.port();
    }

    @Test
    void dispatchAuthorizedGetReturns200WithData(@TempDir File dataFolder) throws Exception {
        String bearer = WebTestTokens.issue(dataFolder, "test-device", "viewer");

        WebAuth auth = new WebAuth(WebTokens.load(dataFolder, Clock.systemUTC()));
        IdentityDto dto = new IdentityDto();
        dto.serverName = "RelayTestServer";
        dto.folia = false;
        dto.serverId = "127.0.0.1:25565";
        IdentityResource identityResource = new IdentityResource(() -> dto);

        int port = startMinimalJavalin(auth, identityResource);
        RelayLoopbackBridge bridge = new RelayLoopbackBridge("http://127.0.0.1:" + port);

        RelayRequestBridge.RelayResponse response = bridge.dispatch(
            "GET",
            "/api/v1/identity",
            Map.of("Authorization", "Bearer " + bearer),
            null
        );

        assertEquals(200, response.status());
        assertTrue(response.body().isJsonObject(), "body must be a JSON object");
        JsonObject body = response.body().getAsJsonObject();
        assertTrue(body.has("data"), "200 body must have 'data' field");
        assertEquals("RelayTestServer", body.getAsJsonObject("data").get("serverName").getAsString());
    }

    @Test
    void dispatchUnauthorizedReturns401Error(@TempDir File dataFolder) throws Exception {

        WebAuth auth = new WebAuth(WebTokens.load(dataFolder, Clock.systemUTC()));
        IdentityDto dto = new IdentityDto();
        dto.serverName = "RelayTestServer";
        dto.folia = false;
        dto.serverId = "127.0.0.1:25565";
        IdentityResource identityResource = new IdentityResource(() -> dto);

        int port = startMinimalJavalin(auth, identityResource);
        RelayLoopbackBridge bridge = new RelayLoopbackBridge("http://127.0.0.1:" + port);

        RelayRequestBridge.RelayResponse response = bridge.dispatch(
            "GET",
            "/api/v1/identity",
            Map.of(),
            null
        );

        assertEquals(401, response.status());
        assertTrue(response.body().isJsonObject(), "body must be a JSON object");
        JsonObject body = response.body().getAsJsonObject();
        assertTrue(body.has("error"), "401 body must have 'error' field, got: " + body);
        assertTrue(
            body.getAsJsonObject("error").has("message"),
            "error must have 'message' field, got: " + body
        );
    }

    @Test
    void theBrowserOriginIsNotForwardedToTheListener() {
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>("unset");
        javalin = Javalin.create();
        javalin.get("/api/v1/ping", ctx -> {
            seen.set(String.valueOf(ctx.header("Origin")));
            ctx.json(Map.of("data", Map.of()));
        });
        javalin.start("127.0.0.1", 0);
        RelayLoopbackBridge bridge = new RelayLoopbackBridge("http://127.0.0.1:" + javalin.port());

        RelayRequestBridge.RelayResponse response = bridge.dispatch("GET", "/api/v1/ping",
            Map.of("Origin", "https://evil.example", "Host", "spoofed"), null);

        assertEquals(200, response.status());
        assertEquals("null", seen.get(), "the relay checks origins itself; the listener sees none");
    }

    @Test
    void dispatchUnreachableReturns502() {
        RelayLoopbackBridge bridge = new RelayLoopbackBridge("http://127.0.0.1:1");

        RelayRequestBridge.RelayResponse response = bridge.dispatch(
            "GET",
            "/api/v1/identity",
            Map.of(),
            null
        );

        assertEquals(502, response.status());
        assertTrue(response.body().isJsonObject(), "body must be a JSON object");
        JsonObject body = response.body().getAsJsonObject();
        assertTrue(body.has("error"), "502 body must have 'error' field");
        assertNotNull(body.getAsJsonObject("error").get("message"), "error must have 'message' field");
    }
}
