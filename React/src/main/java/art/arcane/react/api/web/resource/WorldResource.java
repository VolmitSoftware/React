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
        Map<String, Object> body = readBody(ctx);
        update(ctx, extractWorldKey(body), body);
    }

    public void updateNamed(Context ctx) {
        WebAuth.requireScope(ctx, "op:execute");
        String worldReference = ctx.pathParam("name");
        if (worldReference == null || worldReference.isBlank()) {
            throw new BadRequestResponse("Missing world name");
        }
        Map<String, Object> body = readBody(ctx);
        update(ctx, resolveWorldKey(worldReference), body);
    }

    private void update(Context ctx, String worldKey, Map<String, Object> body) {
        Double budgetMs = extractDouble(body, "budgetMs");
        Double panicMs = extractDouble(body, "panicMs");
        Double releaseMs = extractDouble(body, "releaseMs");
        WorldDto dto = backend.update(worldKey, budgetMs, panicMs, releaseMs);
        if (dto == null) {
            throw new NotFoundResponse("Unknown world: " + worldKey);
        }
        ctx.json(new Envelope<>(dto));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readBody(Context ctx) {
        return ctx.bodyAsClass(Map.class);
    }

    private String resolveWorldKey(String reference) {
        for (WorldDto world : backend.list()) {
            if (reference.equals(world.key) || reference.equals(world.name)) {
                return world.key;
            }
        }
        return reference;
    }

    private static String extractWorldKey(Map<String, Object> body) {
        Object rawWorldKey = body == null ? null : body.get("worldKey");
        if (!(rawWorldKey instanceof String worldKey) || worldKey.isBlank()) {
            throw new BadRequestResponse("Missing worldKey");
        }
        return worldKey;
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
