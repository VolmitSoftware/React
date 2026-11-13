package art.arcane.react.web;

import art.arcane.react.api.web.ActionBackend;
import art.arcane.react.api.web.ActionDispatcher;
import art.arcane.react.api.web.ActionTicketRegistry;
import art.arcane.react.api.web.AuditLog;
import art.arcane.react.api.web.BukkitWebMutationReporter;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.api.web.dto.ActionDescriptorDto;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.resource.ActionResource;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import art.arcane.react.api.web.WebError;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
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

    private WebToken adminToken;
    private WebToken opToken;
    private WebToken readToken;
    private FakeActionBackend fakeBackend;
    private FakeActionDispatcher fakeDispatcher;
    private AuditLog audit;
    private ActionResource resource;

    @BeforeEach
    void setUp() {



        adminToken = WebTestTokens.token("tok-admin", "admin");
        opToken = WebTestTokens.token("tok-op", "operator");
        readToken = WebTestTokens.token("tok-read", "viewer");

        fakeBackend = new FakeActionBackend();
        fakeDispatcher = new FakeActionDispatcher("tkt-1", "queued");
        audit = new AuditLog(tmpDir) {
            @Override
            protected void schedule(Runnable r) {
                r.run();
            }
        };
        resource = new ActionResource(fakeBackend, fakeDispatcher, new BukkitWebMutationReporter(audit), new ActionTicketRegistry(() -> { }));
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
        when(ctx.<WebToken>attribute("token")).thenReturn(opToken);
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
        when(ctx.<WebToken>attribute("token")).thenReturn(adminToken);
        when(ctx.pathParam("id")).thenReturn("purge-entities");
        when(ctx.bodyAsClass(ActionResource.ExecuteBody.class))
                .thenReturn(new ActionResource.ExecuteBody(Map.of(), null));

        assertThrows(ConflictResponse.class, () -> resource.execute(ctx));
        assertFalse(fakeDispatcher.dispatched);
    }

    @Test
    void execute_destructive_with_admin_token_confirm_true_returns_202() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(adminToken);
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
        when(ctx.<WebToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("id")).thenReturn("purge-entities");
        when(ctx.bodyAsClass(ActionResource.ExecuteBody.class))
                .thenReturn(new ActionResource.ExecuteBody(Map.of(), true));

        assertThrows(WebError.class, () -> resource.execute(ctx));
        assertFalse(fakeDispatcher.dispatched);
    }

    @Test
    void execute_unknown_id_throws_not_found() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("id")).thenReturn("does-not-exist");
        when(ctx.bodyAsClass(ActionResource.ExecuteBody.class))
                .thenReturn(new ActionResource.ExecuteBody(Map.of(), null));

        assertThrows(NotFoundResponse.class, () -> resource.execute(ctx));
    }

    @Test
    void execute_with_read_only_token_throws_forbidden_and_dispatcher_not_invoked() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(readToken);
        when(ctx.pathParam("id")).thenReturn("action-hopper-network-normalize");
        when(ctx.bodyAsClass(ActionResource.ExecuteBody.class))
                .thenReturn(new ActionResource.ExecuteBody(Map.of(), null));

        assertThrows(WebError.class, () -> resource.execute(ctx));
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
