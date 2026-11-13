package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebRole;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.api.web.pairing.PairingLink;
import art.arcane.react.api.web.pairing.WebTokens;
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
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The pairing flow over the real listener: link, redeem, failures the page tells apart, the summary, endings and restarts. */
public class PairRoutesIntegrationTest {
    private static final String APP = "http://127.0.0.1:8826";

    private final HttpClient http = HttpClient.newHttpClient();
    private final MovableClock clock = new MovableClock();
    private WebController controller;

    @AfterEach
    void cleanup() {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
    }

    @Test
    void aLinkRedeemsOnceForATokenThatWorksEverywhere(@TempDir File dataFolder) throws Exception {
        start(dataFolder);
        PairingLink link = link(WebRole.ADMIN.scopes(), "laptop");
        assertEquals(controller.getIdentity().fingerprint(), link.fingerprint());
        assertEquals(dataFolder.getName(), link.serverName());
        assertEquals(Optional.of("admin"), link.role());
        assertEquals(Optional.of("http://127.0.0.1:" + controller.getBoundPort()), link.directUrl());
        assertEquals(Optional.empty(), link.relayUrl());

        HttpResponse<String> paired = pair(link, APP, "Chrome on macOS");
        assertEquals(200, paired.statusCode(), paired.body());
        assertEquals(APP, paired.headers().firstValue("access-control-allow-origin").orElse(null));
        JsonObject data = data(paired);
        String token = data.get("token").getAsString();
        assertEquals(link.tokenId(), data.get("tokenId").getAsString());
        assertEquals("admin", data.get("role").getAsString());
        assertEquals("laptop", data.get("label").getAsString());
        assertEquals(604800L, data.get("lifetimeSeconds").getAsLong());
        assertEquals(link.fingerprint(), data.get("fingerprint").getAsString());
        assertEquals(link.serverName(), data.get("serverName").getAsString());
        assertEquals(clock.instant().plus(Duration.ofDays(7)).toString(), data.get("expiresAt").getAsString());
        assertFalse(Files.readString(new File(dataFolder, WebTokens.FILE).toPath()).contains(token.substring(token.indexOf('.') + 1)));

        assertEquals(200, get("/api/v1/whoami", token, APP).statusCode());
        assertEquals(200, get("/api/v1/logs", token, APP).statusCode(), "RBAC holds on the new tokens");
        assertEquals("pairing_used", code(pair(link, APP, null)));
    }

    @Test
    void eachFailureHasItsOwnCode(@TempDir File dataFolder) throws Exception {
        start(dataFolder);

        PairingLink expired = link(WebRole.OPERATOR.scopes(), "");
        clock.advance(Duration.ofMinutes(10));
        HttpResponse<String> late = pair(expired, APP, null);
        assertEquals(410, late.statusCode());
        assertEquals("pairing_expired", code(late));

        PairingLink revoked = link(WebRole.OPERATOR.scopes(), "");
        assertEquals(List.of(revoked.tokenId()), controller.revoke(revoked.tokenId()));
        assertEquals("pairing_revoked", code(pair(revoked, APP, null)));

        PairingLink unknown = new PairingLink(revoked.directUrl(), revoked.relayUrl(), revoked.fingerprint(), revoked.serverKey(),
            "tok_AAAAAAAAAAAAAAAA", revoked.secret(), revoked.expiresAt(), revoked.serverName(), revoked.scopes(), 60, Optional.empty());
        HttpResponse<String> nobody = pair(unknown, APP, null);
        assertEquals(401, nobody.statusCode());
        assertEquals("pairing_unknown", code(nobody));

        PairingLink fresh = link(WebRole.OPERATOR.scopes(), "");
        HttpResponse<String> foreign = pair(fresh, "https://elsewhere.example", null);
        assertEquals(403, foreign.statusCode());
        assertEquals("origin_not_allowed", code(foreign));
        assertEquals("https://elsewhere.example", foreign.headers().firstValue("access-control-allow-origin").orElse(null));
        assertEquals(200, pair(fresh, APP, null).statusCode(), "the refused origin did not spend the link");
    }

    @Test
    void theSummaryCarriesTheStartPageRowAndThePairingEnd(@TempDir File dataFolder) throws Exception {
        start(dataFolder);
        String token = redeem(link(WebRole.VIEWER.scopes(), "phone"));

        HttpResponse<String> response = get("/api/v1/summary", token, APP);
        assertEquals(200, response.statusCode(), response.body());
        JsonObject summary = data(response);
        assertEquals(dataFolder.getName(), summary.get("serverName").getAsString());
        assertEquals(controller.getIdentity().fingerprint(), summary.get("fingerprint").getAsString());
        assertTrue(summary.get("players").isJsonNull(), "nothing sampled yet");
        assertEquals("UNKNOWN", summary.getAsJsonObject("incident").get("state").getAsString());
        assertEquals(0, summary.getAsJsonObject("incident").get("open").getAsInt());
        JsonObject pairing = summary.getAsJsonObject("pairing");
        assertEquals("viewer", pairing.get("role").getAsString());
        assertEquals(clock.instant().plus(Duration.ofDays(7)).toString(), pairing.get("expiresAt").getAsString());
        assertTrue(response.body().length() < 1024, "one small answer: " + response.body().length() + " bytes");
        assertEquals(401, get("/api/v1/summary", null, APP).statusCode());
    }

    @Test
    void anUnusedPairingEndsAndItsSocketsCloseWithTheReason(@TempDir File dataFolder) throws Exception {
        start(dataFolder);
        String token = redeem(new WebTokens.Grant("", WebRole.VIEWER.scopes(), Duration.ofMinutes(5), "console", Optional.empty()));

        clock.advance(Duration.ofMinutes(3));
        HttpResponse<String> whoami = get("/api/v1/whoami", token, APP);
        assertEquals(clock.instant().plus(Duration.ofMinutes(5)).toString(), data(whoami).get("expiresAt").getAsString(), "use slides the end");

        Closing socket = Closing.open(http, URI.create("ws://127.0.0.1:" + controller.getBoundPort() + "/ws/metrics"), token);
        clock.advance(Duration.ofMinutes(6));
        assertEquals(4401, socket.code.get(5, TimeUnit.SECONDS));
        assertEquals("token_expired", socket.reason);
        assertEquals("token_expired", code(get("/api/v1/whoami", token, APP)));
    }

    @Test
    void aRevokedTokenSocketClosesWithTheRevokedReason(@TempDir File dataFolder) throws Exception {
        start(dataFolder);
        String token = redeem(link(WebRole.ADMIN.scopes(), ""));
        Closing socket = Closing.open(http, URI.create("ws://127.0.0.1:" + controller.getBoundPort() + "/ws/logs"), token);
        Thread.sleep(200L);

        controller.revoke(WebTestTokens.idOf(token));

        assertEquals(4401, socket.code.get(5, TimeUnit.SECONDS));
        assertEquals("token_revoked", socket.reason);
        Closing refused = Closing.open(http, URI.create("ws://127.0.0.1:" + controller.getBoundPort() + "/ws/metrics"), token);
        assertEquals(4401, refused.code.get(5, TimeUnit.SECONDS));
        assertEquals("token_revoked", refused.reason);
    }

    @Test
    void tokensIdentityAndFingerprintSurviveARestart(@TempDir File dataFolder) throws Exception {
        start(dataFolder);
        String token = redeem(link(WebRole.OPERATOR.scopes(), ""));
        String fingerprint = controller.getIdentity().fingerprint();
        String revoked = redeem(link(WebRole.OPERATOR.scopes(), ""));
        controller.revoke(WebTestTokens.idOf(revoked));
        PairingLink open = link(WebRole.OPERATOR.scopes(), "");
        controller.stop();
        controller = null;

        start(dataFolder);
        assertEquals(fingerprint, controller.getIdentity().fingerprint());
        assertEquals(200, get("/api/v1/whoami", token, APP).statusCode());
        assertEquals("token_revoked", code(get("/api/v1/whoami", revoked, APP)));
        assertEquals("pairing_unknown", code(pair(open, APP, null)), "links live in memory only");
    }

    @Test
    void theOldPairingStoresAreRemoved(@TempDir File dataFolder) throws Exception {
        File web = new File(dataFolder, "web");
        assertTrue(web.mkdirs());
        Files.writeString(new File(web, "tokens.toml").toPath(), "[[tokens]]\nid = \"rct-old\"\n");
        Files.write(new File(web, "secret.key").toPath(), new byte[32]);

        start(dataFolder);

        assertFalse(new File(web, "tokens.toml").exists());
        assertFalse(new File(web, "secret.key").exists());
        assertTrue(controller.getTokens().list().isEmpty());
    }

    private void start(File dataFolder) throws Exception {
        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());
        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setAllowedOrigins(List.of(APP));
        controller = new WebController() {
            @Override
            protected void executeAsync(Runnable runnable) {
                Thread.ofVirtual().start(runnable);
            }

            @Override
            protected IdentityDto resolveIdentity() {
                IdentityDto dto = new IdentityDto();
                dto.serverName = "PairTest";
                return dto;
            }
        };
        controller.setConfig(config);
        controller.setDataFolder(dataFolder);
        controller.setSampleController(sampleController);
        controller.setClock(clock);
        controller.setIncidentStateSupplier(() -> "UNKNOWN");
        controller.setIncidentRecordsSupplier(limit -> List.of());
        controller.start();
        controller.postStart();
        controller.awaitStart(5000L);
    }

    private PairingLink link(List<String> scopes, String label) {
        return link(new WebTokens.Grant(label, scopes, Duration.ofDays(7), "console", Optional.empty()));
    }

    private PairingLink link(WebTokens.Grant grant) {
        String url = controller.createLink(grant).url();
        assertTrue(url.startsWith(WebConfiguration.DEFAULT_APP_URL + "/pair#RCT3."), url);
        return PairingLink.decode(url.substring(url.indexOf('#') + 1));
    }

    private String redeem(PairingLink link) throws Exception {
        HttpResponse<String> response = pair(link, APP, null);
        assertEquals(200, response.statusCode(), response.body());
        return data(response).get("token").getAsString();
    }

    private String redeem(WebTokens.Grant grant) throws Exception {
        return redeem(link(grant));
    }

    private HttpResponse<String> pair(PairingLink link, String origin, String client) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("tokenId", link.tokenId());
        body.addProperty("secret", link.secret());
        if (client != null) {
            body.addProperty("client", client);
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + controller.getBoundPort() + "/api/v1/pair"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (origin != null) {
            request.header("Origin", origin);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + controller.getBoundPort() + path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (origin != null) {
            request.header("Origin", origin);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject data(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
    }

    private static String code(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("error").get("code").getAsString();
    }

    private static final class Closing implements WebSocket.Listener {
        private final CompletableFuture<Integer> code = new CompletableFuture<>();
        private final String token;
        private volatile String reason;

        private Closing(String token) {
            this.token = token;
        }

        static Closing open(HttpClient http, URI uri, String token) throws Exception {
            Closing listener = new Closing(token);
            http.newWebSocketBuilder().buildAsync(uri, listener).get(5, TimeUnit.SECONDS);
            return listener;
        }

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(Long.MAX_VALUE);
            socket.sendText("{\"type\":\"auth\",\"token\":\"" + token + "\"}", true);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int status, String why) {
            reason = why;
            code.complete(status);
            return null;
        }
    }

    private static final class MovableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-13T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
