package art.arcane.react.web;

import art.arcane.react.api.web.AuditLog;
import art.arcane.react.api.web.BukkitWebMutationReporter;
import art.arcane.react.api.web.ConsoleCommandDispatcher;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.resource.ConsoleResource;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import art.arcane.react.api.web.WebError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConsoleResourceTest {

    @TempDir
    File dataFolder;

    private WebToken adminToken;
    private WebToken operatorToken;
    private AuditLog auditLog;

    @BeforeEach
    void setUp() {
        adminToken = WebTestTokens.token("tok-console-admin", "admin");
        operatorToken = WebTestTokens.token("tok-console-op", "operator");
        auditLog = new AuditLog(dataFolder) {
            @Override
            protected void schedule(Runnable runnable) {
                runnable.run();
            }
        };
    }

    @Test
    void adminCommandIsNormalizedDispatchedAndAuditedWithoutArguments() {
        AtomicReference<String> dispatchedCommand = new AtomicReference<>();
        ConsoleCommandDispatcher dispatcher = command -> {
            dispatchedCommand.set(command);
            return true;
        };
        ConsoleResource resource = new ConsoleResource(
            dispatcher,
            auditLog,
            new BukkitWebMutationReporter(auditLog)
        );
        Context ctx = context(adminToken, "  /lp user alice permission set bearer-secret  ");
        when(ctx.status(202)).thenReturn(ctx);
        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);

        resource.execute(ctx);

        assertEquals("lp user alice permission set bearer-secret", dispatchedCommand.get());
        verify(ctx).status(202);
        verify(ctx).json(responseCaptor.capture());
        Envelope<?> envelope = (Envelope<?>) responseCaptor.getValue();
        ConsoleResource.ExecuteResult result = (ConsoleResource.ExecuteResult) envelope.data();
        assertTrue(result.dispatched());

        List<String> audit = auditLog.tail(1);
        assertEquals(1, audit.size());
        assertTrue(audit.get(0).contains("web:tok-console-admin"));
        assertTrue(audit.get(0).contains("verb=lp"));
        assertFalse(audit.get(0).contains("alice"));
        assertFalse(audit.get(0).contains("bearer-secret"));
    }

    @Test
    void operatorCannotExecuteConsoleCommands() {
        ConsoleCommandDispatcher dispatcher = mock(ConsoleCommandDispatcher.class);
        ConsoleResource resource = new ConsoleResource(
            dispatcher,
            auditLog,
            new BukkitWebMutationReporter(auditLog)
        );
        Context ctx = context(operatorToken, "say denied");

        assertThrows(WebError.class, () -> resource.execute(ctx));
        verify(dispatcher, never()).dispatch(anyString());
    }

    @Test
    void blankOverlongAndControlCharacterCommandsAreRejected() {
        ConsoleCommandDispatcher dispatcher = mock(ConsoleCommandDispatcher.class);
        ConsoleResource resource = new ConsoleResource(
            dispatcher,
            auditLog,
            new BukkitWebMutationReporter(auditLog)
        );
        List<String> invalidCommands = List.of(
            " ",
            "x".repeat(ConsoleResource.MAX_COMMAND_LENGTH + 1),
            "say first\nstop",
            "say\tsecret",
            "say\0secret"
        );

        for (String invalidCommand : invalidCommands) {
            Context ctx = context(adminToken, invalidCommand);
            assertThrows(BadRequestResponse.class, () -> resource.execute(ctx), invalidCommand);
        }
        verify(dispatcher, never()).dispatch(anyString());
    }

    private static Context context(WebToken token, String command) {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(token);
        when(ctx.bodyAsClass(ConsoleResource.ExecuteBody.class)).thenReturn(new ConsoleResource.ExecuteBody(command));
        return ctx;
    }

}
