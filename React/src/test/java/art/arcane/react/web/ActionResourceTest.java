package art.arcane.react.web;

import art.arcane.react.api.web.ActionBackend;
import art.arcane.react.api.web.ActionDispatcher;
import art.arcane.react.api.web.AuditLog;
import art.arcane.react.api.web.BukkitWebMutationReporter;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.dto.ActionDescriptorDto;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.resource.ActionResource;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ActionResourceTest {

    @TempDir
    File tmpDir;

    private PairingToken adminToken;
    private PairingToken opToken;
    private PairingToken readToken;
    private FakeActionBackend fakeBackend;
    private FakeActionDispatcher fakeDispatcher;
    private AuditLog audit;
    private ActionResource resource;

    @BeforeEach
    void setUp() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);

        TokenRecord adminRecord = new TokenRecord("tok-admin", "admin-device", 1000L, Set.of("read", "op:execute", "admin"), "admin");
        TokenRecord opRecord = new TokenRecord("tok-op", "op-device", 1000L, Set.of("read", "op:execute"), "operator");
        TokenRecord readRecord = new TokenRecord("tok-read", "read-device", 1000L, Set.of("read"), "viewer");
        TokenStore store = TokenStore.inMemory(adminRecord, opRecord, readRecord);

        String adminBearer = PairingToken.mint(secret, adminRecord.id(), adminRecord.label(), adminRecord.issuedAt(), adminRecord.scopes());
        String opBearer = PairingToken.mint(secret, opRecord.id(), opRecord.label(), opRecord.issuedAt(), opRecord.scopes());
        String readBearer = PairingToken.mint(secret, readRecord.id(), readRecord.label(), readRecord.issuedAt(), readRecord.scopes());

        adminToken = PairingToken.verify(secret, adminBearer, store).orElseThrow();
        opToken = PairingToken.verify(secret, opBearer, store).orElseThrow();
        readToken = PairingToken.verify(secret, readBearer, store).orElseThrow();

        fakeBackend = new FakeActionBackend();
        fakeDispatcher = new FakeActionDispatcher("tkt-1", "queued");
        audit = new AuditLog(tmpDir) {
            @Override
            protected void schedule(Runnable r) {
                r.run();
            }
        };
        resource = new ActionResource(fakeBackend, fakeDispatcher, new BukkitWebMutationReporter(audit));
    }

    @Test
    void list_returns_all_items_in_data_array() {
        Context ctx = mock(Context.class);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.list(ctx);
        verify(ctx).json(captor.capture());
        ActionResource.ListResponse response = (ActionResource.ListResponse) captor.getValue();
        assertEquals(fakeBackend.list().size(), response.data().length);
    }

    @Test
    void execute_non_destructive_with_op_token_returns_202_and_ticket() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("id")).thenReturn("action-hopper-network-normalize");
        when(ctx.bodyAsClass(ActionResource.ExecuteBody.class))
                .thenReturn(new ActionResource.ExecuteBody(Map.of(), null));

        ArgumentCaptor<Object> jsonCaptor = ArgumentCaptor.forClass(Object.class);
        when(ctx.status(202)).thenReturn(ctx);

        resource.execute(ctx);

        verify(ctx).status(202);
        verify(ctx).json(jsonCaptor.capture());

        @SuppressWarnings("unchecked")
        Envelope<ActionResource.TicketDto> envelope = (Envelope<ActionResource.TicketDto>) jsonCaptor.getValue();
        assertNotNull(envelope.data());
        assertEquals("tkt-1", envelope.data().ticketId());
        assertEquals("queued", envelope.data().status());

        assertTrue(fakeDispatcher.dispatched);
        assertEquals("action-hopper-network-normalize", fakeDispatcher.lastId);

        List<String> tail = audit.tail(1);
        assertEquals(1, tail.size());
        assertTrue(tail.get(0).contains("action.execute"));
        assertTrue(tail.get(0).contains("action-hopper-network-normalize"));
    }

    @Test
    void execute_destructive_with_admin_token_without_confirm_throws_conflict_and_dispatcher_not_invoked() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(adminToken);
        when(ctx.pathParam("id")).thenReturn("purge-entities");
        when(ctx.bodyAsClass(ActionResource.ExecuteBody.class))
                .thenReturn(new ActionResource.ExecuteBody(Map.of(), null));

        assertThrows(ConflictResponse.class, () -> resource.execute(ctx));
        assertFalse(fakeDispatcher.dispatched);
    }

    @Test
    void execute_destructive_with_admin_token_confirm_true_returns_202() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(adminToken);
        when(ctx.pathParam("id")).thenReturn("purge-entities");
        when(ctx.bodyAsClass(ActionResource.ExecuteBody.class))
                .thenReturn(new ActionResource.ExecuteBody(Map.of(), true));
        when(ctx.status(202)).thenReturn(ctx);

        resource.execute(ctx);

        verify(ctx).status(202);
        assertTrue(fakeDispatcher.dispatched);
        assertEquals("purge-entities", fakeDispatcher.lastId);
    }

    @Test
    void execute_destructive_with_op_token_throws_forbidden_and_dispatcher_not_invoked() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("id")).thenReturn("purge-entities");
        when(ctx.bodyAsClass(ActionResource.ExecuteBody.class))
                .thenReturn(new ActionResource.ExecuteBody(Map.of(), true));

        assertThrows(ForbiddenResponse.class, () -> resource.execute(ctx));
        assertFalse(fakeDispatcher.dispatched);
    }

    @Test
    void execute_unknown_id_throws_not_found() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("id")).thenReturn("does-not-exist");
        when(ctx.bodyAsClass(ActionResource.ExecuteBody.class))
                .thenReturn(new ActionResource.ExecuteBody(Map.of(), null));

        assertThrows(NotFoundResponse.class, () -> resource.execute(ctx));
    }

    @Test
    void execute_with_read_only_token_throws_forbidden_and_dispatcher_not_invoked() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(readToken);
        when(ctx.pathParam("id")).thenReturn("action-hopper-network-normalize");
        when(ctx.bodyAsClass(ActionResource.ExecuteBody.class))
                .thenReturn(new ActionResource.ExecuteBody(Map.of(), null));

        assertThrows(ForbiddenResponse.class, () -> resource.execute(ctx));
        assertFalse(fakeDispatcher.dispatched);
    }

    private static final class FakeActionBackend implements ActionBackend {

        private final List<ActionDescriptorDto> items = new ArrayList<>();

        FakeActionBackend() {
            ActionDescriptorDto destructive = new ActionDescriptorDto();
            destructive.id = "purge-entities";
            destructive.name = "Purge Entities";
            destructive.destructive = true;
            destructive.params = new art.arcane.react.api.web.dto.ActionParamDto[0];

            ActionDescriptorDto normal = new ActionDescriptorDto();
            normal.id = "action-hopper-network-normalize";
            normal.name = "Hopper Network Normalize";
            normal.destructive = false;
            normal.params = new art.arcane.react.api.web.dto.ActionParamDto[0];

            items.add(destructive);
            items.add(normal);
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

        private final String ticketId;
        private final String status;
        boolean dispatched = false;
        String lastId;
        Map<String, Object> lastParams;

        FakeActionDispatcher(String ticketId, String status) {
            this.ticketId = ticketId;
            this.status = status;
        }

        @Override
        public DispatchResult dispatch(String actionId, Map<String, Object> params) {
            dispatched = true;
            lastId = actionId;
            lastParams = params;
            return new DispatchResult(ticketId, status);
        }
    }
}
