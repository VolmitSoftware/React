package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.AuditLog;
import art.arcane.react.api.web.ConsoleCommandDispatcher;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.dto.Envelope;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

import java.util.regex.Pattern;

public final class ConsoleResource {

    public static final int MAX_COMMAND_LENGTH = 512;
    private static final Pattern SAFE_VERB = Pattern.compile("[A-Za-z0-9:_-]{1,64}");

    private final ConsoleCommandDispatcher dispatcher;
    private final AuditLog auditLog;

    public ConsoleResource(ConsoleCommandDispatcher dispatcher, AuditLog auditLog) {
        this.dispatcher = dispatcher;
        this.auditLog = auditLog;
    }

    public record ExecuteBody(String command) {}

    public record ExecuteResult(boolean dispatched) {}

    public void execute(Context ctx) {
        WebAuth.requireScope(ctx, "console:execute");
        String command = normalize(ctx.bodyAsClass(ExecuteBody.class));
        String actor = resolveActor(ctx);
        String detail = auditDetail(command);
        try {
            boolean dispatched = dispatcher.dispatch(command);
            auditLog.append(actor, "console.execute", detail, dispatched ? "DISPATCHED" : "REJECTED");
            ctx.status(202).json(new Envelope<>(new ExecuteResult(dispatched)));
        } catch (RuntimeException e) {
            auditLog.append(actor, "console.execute", detail, "ERROR:" + e.getClass().getSimpleName());
            throw e;
        }
    }

    private static String normalize(ExecuteBody body) {
        String raw = body == null ? null : body.command();
        if (raw == null || raw.isBlank()) {
            throw new BadRequestResponse("Missing command");
        }
        if (raw.length() > MAX_COMMAND_LENGTH) {
            throw new BadRequestResponse("Command exceeds " + MAX_COMMAND_LENGTH + " characters");
        }
        for (int index = 0; index < raw.length(); index++) {
            if (Character.isISOControl(raw.charAt(index))) {
                throw new BadRequestResponse("Command must not contain control characters");
            }
        }
        String command = raw.trim();
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isBlank()) {
            throw new BadRequestResponse("Missing command");
        }
        return command;
    }

    private static String resolveActor(Context ctx) {
        PairingToken token = ctx.attribute("token");
        return token == null ? "web" : "web:" + token.tokenId();
    }

    private static String auditDetail(String command) {
        int separator = command.indexOf(' ');
        String verb = separator < 0 ? command : command.substring(0, separator);
        String safeVerb = SAFE_VERB.matcher(verb).matches() ? verb : "other";
        return "verb=" + safeVerb + " length=" + command.length();
    }
}
