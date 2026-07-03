package art.arcane.react.web;

import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WorldBackend;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.WorldDto;
import art.arcane.react.api.web.resource.WorldResource;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldResourceTest {

    private PairingToken opToken;
    private PairingToken readToken;
    private FakeWorldBackend fakeBackend;
    private WorldResource resource;

    @BeforeEach
    void setUp() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);

        TokenRecord opRecord = new TokenRecord("tok-op", "op-device", 1000L, Set.of("read", "op:execute"), "operator");
        TokenRecord readRecord = new TokenRecord("tok-read", "read-device", 1000L, Set.of("read"), "viewer");
        TokenStore store = TokenStore.inMemory(opRecord, readRecord);

        String opBearer = PairingToken.mint(secret, opRecord.id(), opRecord.label(), opRecord.issuedAt(), opRecord.scopes());
        String readBearer = PairingToken.mint(secret, readRecord.id(), readRecord.label(), readRecord.issuedAt(), readRecord.scopes());

        opToken = PairingToken.verify(secret, opBearer, store).orElseThrow();
        readToken = PairingToken.verify(secret, readBearer, store).orElseThrow();

        fakeBackend = new FakeWorldBackend();
        resource = new WorldResource(fakeBackend);
    }

    @Test
    void list_returns_ListResponse_with_data_array() {
        Context ctx = mock(Context.class);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.list(ctx);
        verify(ctx).json(captor.capture());

        WorldResource.ListResponse response = (WorldResource.ListResponse) captor.getValue();
        assertNotNull(response);
        assertEquals(fakeBackend.list().size(), response.data().length);
        assertEquals("world", response.data()[0].name);
    }

    @Test
    void update_with_op_token_invokes_backend_update_and_returns_envelope() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("name")).thenReturn("world");
        Map<String, Object> body = new HashMap<>();
        body.put("budgetMs", 40.0);
        when(ctx.bodyAsClass(Map.class)).thenReturn(body);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.update(ctx);
        verify(ctx).json(captor.capture());

        @SuppressWarnings("unchecked")
        Envelope<WorldDto> envelope = (Envelope<WorldDto>) captor.getValue();
        assertNotNull(envelope.data());
        assertEquals("world", envelope.data().name);

        assertEquals("world", fakeBackend.lastUpdateName);
        assertEquals(40.0, fakeBackend.lastUpdateBudgetMs, 1e-9);
        assertNull(fakeBackend.lastUpdatePanicMs);
        assertNull(fakeBackend.lastUpdateReleaseMs);
    }

    @Test
    void update_unknown_world_backend_returns_null_throws_not_found() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("name")).thenReturn("unknown-world");
        Map<String, Object> body = new HashMap<>();
        body.put("budgetMs", 40.0);
        when(ctx.bodyAsClass(Map.class)).thenReturn(body);

        assertThrows(NotFoundResponse.class, () -> resource.update(ctx));
    }

    @Test
    void update_with_read_only_token_throws_forbidden() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(readToken);
        when(ctx.pathParam("name")).thenReturn("world");

        assertThrows(ForbiddenResponse.class, () -> resource.update(ctx));
    }

    @Test
    void update_with_non_numeric_budget_throws_bad_request() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("name")).thenReturn("world");
        Map<String, Object> body = new HashMap<>();
        body.put("budgetMs", "not-a-number");
        when(ctx.bodyAsClass(Map.class)).thenReturn(body);

        assertThrows(BadRequestResponse.class, () -> resource.update(ctx));
    }

    private static final class FakeWorldBackend implements WorldBackend {

        private final List<WorldDto> worlds = new ArrayList<>();
        String lastUpdateName;
        Double lastUpdateBudgetMs;
        Double lastUpdatePanicMs;
        Double lastUpdateReleaseMs;

        FakeWorldBackend() {
            WorldDto w = new WorldDto();
            w.name = "world";
            w.pressureMode = "NORMAL";
            w.budgetMs = 35.0;
            w.panicMs = 50.0;
            w.releaseMs = 28.0;
            worlds.add(w);
        }

        @Override
        public List<WorldDto> list() {
            return worlds;
        }

        @Override
        public WorldDto update(String name, Double budgetMs, Double panicMs, Double releaseMs) {
            lastUpdateName = name;
            lastUpdateBudgetMs = budgetMs;
            lastUpdatePanicMs = panicMs;
            lastUpdateReleaseMs = releaseMs;
            for (WorldDto w : worlds) {
                if (w.name.equals(name)) {
                    if (budgetMs != null) {
                        w.budgetMs = budgetMs;
                    }
                    if (panicMs != null) {
                        w.panicMs = panicMs;
                    }
                    if (releaseMs != null) {
                        w.releaseMs = releaseMs;
                    }
                    return w;
                }
            }
            return null;
        }
    }
}
