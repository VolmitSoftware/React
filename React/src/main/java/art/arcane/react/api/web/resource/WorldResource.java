package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WorldBackend;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.WorldDto;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.Map;

public class WorldResource {

    private final WorldBackend backend;

    public WorldResource(WorldBackend backend) {
        this.backend = backend;
    }

    public record ListResponse(WorldDto[] data) {}

    public void list(Context ctx) {
        ctx.json(new ListResponse(backend.list().toArray(new WorldDto[0])));
    }

    public void update(Context ctx) {
        WebAuth.requireScope(ctx, "op:execute");
        String name = ctx.pathParam("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Double budgetMs = extractDouble(body, "budgetMs");
        Double panicMs = extractDouble(body, "panicMs");
        Double releaseMs = extractDouble(body, "releaseMs");
        WorldDto dto = backend.update(name, budgetMs, panicMs, releaseMs);
        if (dto == null) {
            throw new NotFoundResponse("Unknown world: " + name);
        }
        ctx.json(new Envelope<>(dto));
    }

    private static Double extractDouble(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key)) {
            return null;
        }
        Object raw = body.get(key);
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                throw new BadRequestResponse("Non-numeric value for field: " + key);
            }
        }
        throw new BadRequestResponse("Non-numeric value for field: " + key);
    }
}
