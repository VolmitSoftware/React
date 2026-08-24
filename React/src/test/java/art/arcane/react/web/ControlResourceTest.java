package art.arcane.react.web;

import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.ControlBackend;
import art.arcane.react.api.web.dto.ControlItemDto;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.resource.ControlResource;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ControlResourceTest {

    private PairingToken opToken;
    private PairingToken readToken;
    private FakeControlBackend fakeBackend;
    private ControlResource resource;

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

        fakeBackend = new FakeControlBackend();
        resource = new ControlResource(fakeBackend, "feature", (context, mutation) -> {
        });
    }

    @Test
    void list_returns_all_items_in_data_array() {
        Context ctx = mock(Context.class);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.list(ctx);
        verify(ctx).json(captor.capture());

        ControlResource.ListResponse response = (ControlResource.ListResponse) captor.getValue();
        assertEquals(fakeBackend.list().size(), response.data().length);
        assertEquals("feature-a", response.data()[0].id);
        assertEquals("feature-b", response.data()[1].id);
    }

    @Test
    void get_known_id_returns_envelope_with_dto() {
        Context ctx = mock(Context.class);
        when(ctx.pathParam("id")).thenReturn("feature-a");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.get(ctx);
        verify(ctx).json(captor.capture());

        @SuppressWarnings("unchecked")
        Envelope<ControlItemDto> envelope = (Envelope<ControlItemDto>) captor.getValue();
        assertEquals("feature-a", envelope.data().id);
    }

    @Test
    void get_unknown_id_throws_not_found() {
        Context ctx = mock(Context.class);
        when(ctx.pathParam("id")).thenReturn("does-not-exist");

        assertThrows(NotFoundResponse.class, () -> resource.get(ctx));
    }

    @Test
    void toggle_with_op_token_sets_enabled_and_returns_envelope() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("id")).thenReturn("feature-a");
        when(ctx.bodyAsClass(ControlResource.ToggleBody.class))
                .thenReturn(new ControlResource.ToggleBody(true));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.toggle(ctx);
        verify(ctx).json(captor.capture());

        @SuppressWarnings("unchecked")
        Envelope<ControlItemDto> envelope = (Envelope<ControlItemDto>) captor.getValue();
        assertEquals("feature-a", envelope.data().id);
        assertTrue(fakeBackend.setEnabledCalled);
        assertEquals("feature-a", fakeBackend.lastSetEnabledId);
        assertTrue(fakeBackend.lastSetEnabledValue);
    }

    @Test
    void toggle_with_read_only_token_throws_forbidden_and_backend_not_invoked() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(readToken);
        when(ctx.pathParam("id")).thenReturn("feature-a");
        when(ctx.bodyAsClass(ControlResource.ToggleBody.class))
                .thenReturn(new ControlResource.ToggleBody(true));

        assertThrows(ForbiddenResponse.class, () -> resource.toggle(ctx));
        assertTrue(!fakeBackend.setEnabledCalled);
    }

    @Test
    void toggle_with_null_enabled_throws_bad_request() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("id")).thenReturn("feature-a");
        when(ctx.bodyAsClass(ControlResource.ToggleBody.class))
                .thenReturn(new ControlResource.ToggleBody(null));

        assertThrows(BadRequestResponse.class, () -> resource.toggle(ctx));
    }

    @Test
    void toggle_unknown_id_throws_not_found() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("id")).thenReturn("does-not-exist");
        when(ctx.bodyAsClass(ControlResource.ToggleBody.class))
                .thenReturn(new ControlResource.ToggleBody(false));

        assertThrows(NotFoundResponse.class, () -> resource.toggle(ctx));
    }

    @Test
    void config_with_op_token_sets_knobs_and_returns_envelope() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("id")).thenReturn("feature-a");
        Map<String, Object> knobs = new HashMap<>();
        knobs.put("budgetMs", 40.0);
        when(ctx.bodyAsClass(Map.class)).thenReturn(knobs);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.config(ctx);
        verify(ctx).json(captor.capture());

        @SuppressWarnings("unchecked")
        Envelope<ControlItemDto> envelope = (Envelope<ControlItemDto>) captor.getValue();
        assertEquals("feature-a", envelope.data().id);
        assertTrue(fakeBackend.setKnobsCalled);
        assertEquals("feature-a", fakeBackend.lastSetKnobsId);
        assertEquals(40.0, fakeBackend.lastSetKnobsMap.get("budgetMs"));
    }

    @Test
    void config_with_empty_map_throws_bad_request() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("id")).thenReturn("feature-a");
        when(ctx.bodyAsClass(Map.class)).thenReturn(Map.of());

        assertThrows(BadRequestResponse.class, () -> resource.config(ctx));
    }

    @Test
    void config_with_read_only_token_throws_forbidden() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(readToken);
        when(ctx.pathParam("id")).thenReturn("feature-a");
        when(ctx.bodyAsClass(Map.class)).thenReturn(Map.of("budgetMs", 40.0));

        assertThrows(ForbiddenResponse.class, () -> resource.config(ctx));
    }

    private static final class FakeControlBackend implements ControlBackend {

        private final List<ControlItemDto> items = new ArrayList<>();
        boolean setEnabledCalled = false;
        String lastSetEnabledId;
        boolean lastSetEnabledValue;
        boolean setKnobsCalled = false;
        String lastSetKnobsId;
        Map<String, Object> lastSetKnobsMap;

        FakeControlBackend() {
            ControlItemDto a = new ControlItemDto();
            a.id = "feature-a";
            a.name = "Feature A";
            a.enabled = false;

            ControlItemDto b = new ControlItemDto();
            b.id = "feature-b";
            b.name = "Feature B";
            b.enabled = true;

            items.add(a);
            items.add(b);
        }

        @Override
        public List<ControlItemDto> list() {
            return items;
        }

        @Override
        public ControlItemDto get(String id) {
            for (ControlItemDto item : items) {
                if (item.id.equals(id)) {
                    return item;
                }
            }
            return null;
        }

        @Override
        public ControlItemDto setEnabled(String id, boolean enabled) {
            setEnabledCalled = true;
            lastSetEnabledId = id;
            lastSetEnabledValue = enabled;
            ControlItemDto item = get(id);
            if (item == null) {
                return null;
            }
            item.enabled = enabled;
            return item;
        }

        @Override
        public ControlItemDto setKnobs(String id, Map<String, Object> knobs) {
            setKnobsCalled = true;
            lastSetKnobsId = id;
            lastSetKnobsMap = knobs;
            return get(id);
        }
    }
}
