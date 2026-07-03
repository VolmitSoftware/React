package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.ActionBackend;
import art.arcane.react.api.web.ActionDispatcher;
import art.arcane.react.api.web.AuditLog;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebSecret;
import art.arcane.react.api.web.dto.ActionDescriptorDto;
import art.arcane.react.api.web.dto.ActionParamDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.api.web.dto.IdentityDto;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ActionRoutesIntegrationTest {

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
    void actionRoutesHappyPathAndAuthGuards(@TempDir File dataFolder) throws Exception {
        byte[] secret = WebSecret.load(dataFolder);
        TokenRecord adminRecord = new TokenRecord("tok-admin", "admin-device", 1000L, Set.of("read", "op:execute", "admin"), "admin");
        TokenRecord opRecord = new TokenRecord("tok-op", "op-device", 1000L, Set.of("read", "op:execute"), "operator");
        TokenRecord readRecord = new TokenRecord("tok-read", "read-device", 1000L, Set.of("read"), "viewer");
        String adminBearer = PairingToken.mint(secret, adminRecord.id(), adminRecord.label(), adminRecord.issuedAt(), adminRecord.scopes());
        String opBearer = PairingToken.mint(secret, opRecord.id(), opRecord.label(), opRecord.issuedAt(), opRecord.scopes());
        String readBearer = PairingToken.mint(secret, readRecord.id(), readRecord.label(), readRecord.issuedAt(), readRecord.scopes());
        File tokensFile = new File(dataFolder, "web/tokens.toml");
        TokenStore store = TokenStore.fromToml(tokensFile);
        store.add(adminRecord);
        store.add(opRecord);
        store.add(readRecord);
        store.save(tokensFile);

        FakeActionBackend fakeBackend = new FakeActionBackend();
        FakeActionDispatcher fakeDispatcher = new FakeActionDispatcher();

        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());

        WebConfiguration config = new WebConfiguration();
        config.setEnabled(true);
        config.setBindAddress("127.0.0.1");
        config.setPort(0);

        controller = buildController(config, dataFolder, sampleController);
        controller.setActionBackend(fakeBackend);
        controller.setActionDispatcher(fakeDispatcher);
        controller.setAuditLog(new AuditLog(dataFolder));
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);

        HttpClient client = HttpClient.newHttpClient();
        AtomicLong opCounter = new AtomicLong(0);
        AtomicLong adminCounter = new AtomicLong(0);

        HttpRequest listRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/actions"))
            .header("Authorization", "Bearer " + readBearer)
            .GET()
            .build();
        HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode(),
            "GET /api/v1/actions with read token should return 200, body: " + listResponse.body());
        JsonObject listRoot = JsonParser.parseString(listResponse.body()).getAsJsonObject();
        JsonArray listData = listRoot.getAsJsonArray("data");
        assertNotNull(listData, "Response should contain 'data' array");
        assertEquals(2, listData.size(), "data array should contain 2 actions, body: " + listResponse.body());

        JsonObject purgeEntry = null;
        JsonObject hopperEntry = null;
        for (int i = 0; i < listData.size(); i++) {
            JsonObject entry = listData.get(i).getAsJsonObject();
            String id = entry.get("id").getAsString();
            if ("purge-entities".equals(id)) {
                purgeEntry = entry;
            } else if ("hopper-network-normalize".equals(id)) {
                hopperEntry = entry;
            }
        }
        assertNotNull(purgeEntry, "purge-entities should be in the list");
        assertTrue(purgeEntry.get("destructive").getAsBoolean(),
            "purge-entities should be destructive");
        JsonArray purgeParams = purgeEntry.getAsJsonArray("params");
        assertTrue(purgeParams != null && purgeParams.size() > 0,
            "purge-entities should have at least one param");
        JsonObject firstParam = purgeParams.get(0).getAsJsonObject();
        assertEquals("secondsToPurge", firstParam.get("key").getAsString(),
            "first param key should be secondsToPurge");
        assertTrue(firstParam.has("default"),
            "param should have a 'default' key present, got: " + firstParam);
        assertNotNull(hopperEntry, "hopper-network-normalize should be in the list");
        assertFalse(hopperEntry.get("destructive").getAsBoolean(),
            "hopper-network-normalize should not be destructive");

        long c1 = opCounter.incrementAndGet();
        HttpRequest executeNonDestructive = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/actions/hopper-network-normalize/execute"))
            .header("Authorization", "Bearer " + opBearer)
            .header("X-React-Counter", String.valueOf(c1))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"params\":{},\"confirm\":false}"))
            .build();
        HttpResponse<String> executeNonDestructiveResponse = client.send(executeNonDestructive, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, executeNonDestructiveResponse.statusCode(),
            "POST hopper-network-normalize/execute with op token should return 202, body: " + executeNonDestructiveResponse.body());
        JsonObject execData = JsonParser.parseString(executeNonDestructiveResponse.body()).getAsJsonObject().getAsJsonObject("data");
        assertNotNull(execData, "Response should contain 'data' object");
        assertEquals("tkt-int", execData.get("ticketId").getAsString());
        assertEquals("queued", execData.get("status").getAsString());
        assertTrue(fakeDispatcher.dispatched, "Dispatcher should have been invoked");
        assertEquals("hopper-network-normalize", fakeDispatcher.lastId);

        fakeDispatcher.dispatched = false;
        long ac1 = adminCounter.incrementAndGet();
        HttpRequest executePurgeNoConfirm = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/actions/purge-entities/execute"))
            .header("Authorization", "Bearer " + adminBearer)
            .header("X-React-Counter", String.valueOf(ac1))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"params\":{\"secondsToPurge\":3},\"confirm\":false}"))
            .build();
        HttpResponse<String> executePurgeNoConfirmResponse = client.send(executePurgeNoConfirm, HttpResponse.BodyHandlers.ofString());
        assertEquals(409, executePurgeNoConfirmResponse.statusCode(),
            "POST purge-entities with admin but no confirm should return 409, body: " + executePurgeNoConfirmResponse.body());
        JsonObject purgeNoConfirmBody = JsonParser.parseString(executePurgeNoConfirmResponse.body()).getAsJsonObject();
        assertTrue(purgeNoConfirmBody.has("error"), "409 body must have 'error' object");
        assertTrue(purgeNoConfirmBody.getAsJsonObject("error").has("message"), "error must have 'message'");
        assertFalse(fakeDispatcher.dispatched, "Dispatcher must NOT be invoked when confirm=false for destructive action");

        long ac2 = adminCounter.incrementAndGet();
        HttpRequest executePurgeConfirm = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/actions/purge-entities/execute"))
            .header("Authorization", "Bearer " + adminBearer)
            .header("X-React-Counter", String.valueOf(ac2))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"params\":{\"secondsToPurge\":3},\"confirm\":true}"))
            .build();
        HttpResponse<String> executePurgeConfirmResponse = client.send(executePurgeConfirm, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, executePurgeConfirmResponse.statusCode(),
            "POST purge-entities with admin + confirm=true should return 202, body: " + executePurgeConfirmResponse.body());
        assertTrue(fakeDispatcher.dispatched, "Dispatcher should be invoked for confirmed destructive action by admin");
        assertEquals("purge-entities", fakeDispatcher.lastId);
        assertNotNull(fakeDispatcher.lastParams, "Dispatcher should receive params");
        assertTrue(fakeDispatcher.lastParams.containsKey("secondsToPurge"),
            "Dispatcher params should contain secondsToPurge");

        fakeDispatcher.dispatched = false;
        long c2 = opCounter.incrementAndGet();
        HttpRequest executePurgeOpConfirm = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/actions/purge-entities/execute"))
            .header("Authorization", "Bearer " + opBearer)
            .header("X-React-Counter", String.valueOf(c2))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"params\":{\"secondsToPurge\":3},\"confirm\":true}"))
            .build();
        HttpResponse<String> executePurgeOpConfirmResponse = client.send(executePurgeOpConfirm, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, executePurgeOpConfirmResponse.statusCode(),
            "POST purge-entities with op token (no admin scope) should return 403, body: " + executePurgeOpConfirmResponse.body());
        JsonObject opPurge403Body = JsonParser.parseString(executePurgeOpConfirmResponse.body()).getAsJsonObject();
        assertTrue(opPurge403Body.has("error"), "403 body must have 'error' object");
        assertTrue(opPurge403Body.getAsJsonObject("error").has("message"), "error must have 'message'");
        assertFalse(fakeDispatcher.dispatched, "Dispatcher must NOT be invoked when operator lacks admin scope for destructive action");

        fakeDispatcher.dispatched = false;
        AtomicLong readCounter = new AtomicLong(0);
        long rc1 = readCounter.incrementAndGet();
        HttpRequest executeWithReadToken = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/actions/hopper-network-normalize/execute"))
            .header("Authorization", "Bearer " + readBearer)
            .header("X-React-Counter", String.valueOf(rc1))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"params\":{},\"confirm\":false}"))
            .build();
        HttpResponse<String> readTokenResponse = client.send(executeWithReadToken, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, readTokenResponse.statusCode(),
            "POST execute with read-only token should return 403, body: " + readTokenResponse.body());
        JsonObject forbiddenBody = JsonParser.parseString(readTokenResponse.body()).getAsJsonObject();
        assertTrue(forbiddenBody.has("error"), "403 body must have 'error' object");
        assertTrue(forbiddenBody.getAsJsonObject("error").has("message"), "error must have 'message'");
        assertFalse(fakeDispatcher.dispatched, "Dispatcher must NOT be invoked when scope check fails");

        HttpRequest executeNoCounter = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/actions/hopper-network-normalize/execute"))
            .header("Authorization", "Bearer " + opBearer)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"params\":{},\"confirm\":false}"))
            .build();
        HttpResponse<String> noCounterResponse = client.send(executeNoCounter, HttpResponse.BodyHandlers.ofString());
        assertEquals(409, noCounterResponse.statusCode(),
            "POST without X-React-Counter should return 409, body: " + noCounterResponse.body());
        JsonObject noCounterBody = JsonParser.parseString(noCounterResponse.body()).getAsJsonObject();
        assertTrue(noCounterBody.has("error"), "409 body must have 'error' object");
        assertTrue(noCounterBody.getAsJsonObject("error").has("message"), "error must have 'message'");

        long ac3 = adminCounter.incrementAndGet();
        HttpRequest executeUnknown = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/actions/does-not-exist/execute"))
            .header("Authorization", "Bearer " + adminBearer)
            .header("X-React-Counter", String.valueOf(ac3))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"params\":{},\"confirm\":true}"))
            .build();
        HttpResponse<String> unknownResponse = client.send(executeUnknown, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, unknownResponse.statusCode(),
            "POST execute with unknown id should return 404, body: " + unknownResponse.body());
        JsonObject unknownBody = JsonParser.parseString(unknownResponse.body()).getAsJsonObject();
        assertTrue(unknownBody.has("error"), "404 body must have 'error' object");
        assertTrue(unknownBody.getAsJsonObject("error").has("message"), "error must have 'message'");
    }

    private static final class FakeActionBackend implements ActionBackend {

        private final List<ActionDescriptorDto> items = new ArrayList<>();

        FakeActionBackend() {
            ActionParamDto secondsToPurge = new ActionParamDto();
            secondsToPurge.key = "secondsToPurge";
            secondsToPurge.label = "Seconds to Purge";
            secondsToPurge.type = "int";
            secondsToPurge.required = false;
            secondsToPurge.defaultValue = 5;

            ActionDescriptorDto purge = new ActionDescriptorDto();
            purge.id = "purge-entities";
            purge.name = "Purge Entities";
            purge.destructive = true;
            purge.params = new ActionParamDto[]{secondsToPurge};

            ActionDescriptorDto hopper = new ActionDescriptorDto();
            hopper.id = "hopper-network-normalize";
            hopper.name = "Hopper Network Normalize";
            hopper.destructive = false;
            hopper.params = new ActionParamDto[0];

            items.add(purge);
            items.add(hopper);
        }

        @Override
        public List<ActionDescriptorDto> list() {
            return items;
        }

        @Override
        public ActionDescriptorDto get(String id) {
            for (ActionDescriptorDto item : items) {
                if (item.id.equals(id)) {
                    return item;
                }
            }
            return null;
        }
    }

    private static final class FakeActionDispatcher implements ActionDispatcher {

        boolean dispatched = false;
        String lastId;
        Map<String, Object> lastParams;

        @Override
        public DispatchResult dispatch(String actionId, Map<String, Object> params) {
            dispatched = true;
            lastId = actionId;
            lastParams = params;
            return new DispatchResult("tkt-int", "queued");
        }
    }
}
