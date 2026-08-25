package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.PlayerBackend;
import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WebMutation;
import art.arcane.react.api.web.WebMutationReporter;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.OnlinePlayerDto;
import art.arcane.react.api.web.dto.PlayerTeleportDto;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerResource {

    public static final int MAX_BLOCK_COORDINATE = 29_999_984;

    private final PlayerBackend backend;
    private final WebMutationReporter reporter;

    public PlayerResource(PlayerBackend backend, WebMutationReporter reporter) {
        this.backend = backend;
        this.reporter = reporter;
    }

    public void list(Context ctx) {
        WebAuth.requireScope(ctx, "admin");
        List<OnlinePlayerDto> players = backend.list();
        ctx.json(new ListResponse(players.toArray(new OnlinePlayerDto[0])));
    }

    public void teleport(Context ctx) {
        WebAuth.requireScope(ctx, "admin");
        UUID playerId = parsePlayerId(ctx.pathParam("id"));
        Map<String, Object> body = readBody(ctx);
        String worldKey = requireWorldKey(body);
        int blockX = requireCoordinate(body, "blockX");
        int blockZ = requireCoordinate(body, "blockZ");
        requireConfirmation(body);
        PlayerBackend.TeleportResult result = backend.queueTeleport(
            playerId,
            worldKey,
            blockX,
            blockZ
        );
        switch (result.status()) {
            case PLAYER_OFFLINE -> throw new NotFoundResponse("Player is not online");
            case WORLD_UNAVAILABLE -> throw new NotFoundResponse("Unknown world: " + worldKey);
            case OUTSIDE_BORDER -> throw new ConflictResponse("Target is outside the world border");
            case REJECTED -> throw new ConflictResponse("Teleport could not be queued");
            case QUEUED -> {
                reporter.report(ctx, new WebMutation(
                    "player.teleport",
                    "player:" + playerId,
                    "world=" + worldKey + " x=" + blockX + " z=" + blockZ,
                    "QUEUED"
                ));
                PlayerTeleportDto dto = new PlayerTeleportDto(
                    playerId.toString(),
                    result.playerName(),
                    "queued",
                    worldKey,
                    blockX,
                    blockZ
                );
                ctx.status(202).json(new Envelope<>(dto));
            }
        }
    }

    private static UUID parsePlayerId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestResponse("Missing player id");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestResponse("Invalid player id");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readBody(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        if (body == null) {
            throw new BadRequestResponse("Missing teleport body");
        }
        return body;
    }

    private static String requireWorldKey(Map<String, Object> body) {
        Object value = body.get("worldKey");
        if (!(value instanceof String worldKey) || worldKey.isBlank()) {
            throw new BadRequestResponse("Missing worldKey");
        }
        return worldKey.trim();
    }

    private static int requireCoordinate(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof Number number)) {
            throw new BadRequestResponse("Missing integral " + key);
        }
        double decimal = number.doubleValue();
        if (!Double.isFinite(decimal) || decimal != Math.rint(decimal)) {
            throw new BadRequestResponse("Non-integral " + key);
        }
        if (decimal < -MAX_BLOCK_COORDINATE || decimal > MAX_BLOCK_COORDINATE) {
            throw new BadRequestResponse(key + " is outside the supported world range");
        }
        return (int) decimal;
    }

    private static void requireConfirmation(Map<String, Object> body) {
        if (!Boolean.TRUE.equals(body.get("confirm"))) {
            throw new BadRequestResponse("Teleport requires confirm=true");
        }
    }

    public record ListResponse(OnlinePlayerDto[] data) {
    }
}
