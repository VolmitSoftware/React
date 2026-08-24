package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.ControlBackend;
import art.arcane.react.api.web.WebMutation;
import art.arcane.react.api.web.WebMutationReporter;
import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.dto.ControlItemDto;
import art.arcane.react.api.web.dto.Envelope;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.Map;

public class ControlResource {

    private final ControlBackend backend;
    private final String controlType;
    private final WebMutationReporter reporter;

    public ControlResource(ControlBackend backend, String controlType, WebMutationReporter reporter) {
        this.backend = backend;
        this.controlType = controlType;
        this.reporter = reporter;
    }

    public record ListResponse(ControlItemDto[] data) {}

    public record ToggleBody(Boolean enabled) {}

    public void list(Context ctx) {
        ctx.json(new ListResponse(backend.list().toArray(new ControlItemDto[0])));
    }

    public void get(Context ctx) {
        String id = ctx.pathParam("id");
        ControlItemDto item = backend.get(id);
        if (item == null) {
            throw new NotFoundResponse("Unknown id: " + id);
        }
        ctx.json(new Envelope<>(item));
    }

    public void toggle(Context ctx) {
        WebAuth.requireScope(ctx, "op:execute");
        ToggleBody body = ctx.bodyAsClass(ToggleBody.class);
        if (body == null || body.enabled() == null) {
            throw new BadRequestResponse("Missing 'enabled' boolean");
        }
        String id = ctx.pathParam("id");
        ControlItemDto item = backend.setEnabled(id, body.enabled());
        if (item == null) {
            throw new NotFoundResponse("Unknown id: " + id);
        }
        reporter.report(ctx, new WebMutation(
            "control.toggle",
            controlType + ":" + id,
            body.enabled() ? "enabled" : "disabled",
            "APPLIED"
        ));
        ctx.json(new Envelope<>(item));
    }

    @SuppressWarnings("unchecked")
    public void config(Context ctx) {
        WebAuth.requireScope(ctx, "op:execute");
        Map<String, Object> knobs = ctx.bodyAsClass(Map.class);
        if (knobs == null || knobs.isEmpty()) {
            throw new BadRequestResponse("No knob values supplied");
        }
        String id = ctx.pathParam("id");
        ControlItemDto item = backend.setKnobs(id, knobs);
        if (item == null) {
            throw new NotFoundResponse("Unknown id: " + id);
        }
        reporter.report(ctx, new WebMutation(
            "control.config",
            controlType + ":" + id,
            "updated " + knobs.size() + " value(s): " + String.join(", ", knobs.keySet()),
            "APPLIED"
        ));
        ctx.json(new Envelope<>(item));
    }
}
