package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.ActionBackend;
import art.arcane.react.api.web.ActionDispatcher;
import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WebMutation;
import art.arcane.react.api.web.WebMutationReporter;
import art.arcane.react.api.web.dto.ActionDescriptorDto;
import art.arcane.react.api.web.dto.Envelope;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.Map;

public class ActionResource {

    private final ActionBackend backend;
    private final ActionDispatcher dispatcher;
    private final WebMutationReporter reporter;

    public ActionResource(
            ActionBackend backend,
            ActionDispatcher dispatcher,
            WebMutationReporter reporter) {
        this.backend = backend;
        this.dispatcher = dispatcher;
        this.reporter = reporter;
    }

    public record ListResponse(ActionDescriptorDto[] data) {}

    public record ExecuteBody(Map<String, Object> params, Boolean confirm) {}

    public record TicketDto(String ticketId, String status) {}

    public void list(Context ctx) {
        ctx.json(new ListResponse(backend.list().toArray(new ActionDescriptorDto[0])));
    }

    public void execute(Context ctx) {
        WebAuth.requireScope(ctx, "op:execute");
        String id = ctx.pathParam("id");
        ActionDescriptorDto descriptor = backend.get(id);
        if (descriptor == null) {
            throw new NotFoundResponse("Unknown action: " + id);
        }
        ExecuteBody body = ctx.bodyAsClass(ExecuteBody.class);
        Map<String, Object> params = (body == null || body.params() == null) ? Map.of() : body.params();
        boolean confirm = body != null && Boolean.TRUE.equals(body.confirm());
        if (descriptor.destructive) {
            WebAuth.requireScope(ctx, "admin");
        }
        if (descriptor.destructive && !confirm) {
            throw new ConflictResponse("Destructive action requires confirm:true");
        }
        ActionDispatcher.DispatchResult result = dispatcher.dispatch(id, params);
        reporter.report(ctx, new WebMutation(
            "action.execute",
            "action:" + id,
            descriptor.destructive ? "queued destructive action" : "queued action",
            "QUEUED:" + result.ticketId()
        ));
        ctx.status(202).json(new Envelope<>(new TicketDto(result.ticketId(), result.status())));
    }
}
