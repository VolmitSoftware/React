package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.RingLogHandler;
import art.arcane.react.api.web.WebAuth;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

import java.util.function.IntFunction;

public class LogsResource {

    private final IntFunction<RingLogHandler.Tail> tailSupplier;

    public LogsResource(IntFunction<RingLogHandler.Tail> tailSupplier) {
        this.tailSupplier = tailSupplier;
    }

    public record LogsResponse(String[] data, long lastSeq) {}

    public void list(Context ctx) {
        WebAuth.requireScope(ctx, "console:read");
        String limitParam = ctx.queryParam("limit");
        int limit = 200;
        if (limitParam != null) {
            try {
                limit = Integer.parseInt(limitParam);
            } catch (NumberFormatException e) {
                throw new BadRequestResponse("Invalid limit: " + limitParam);
            }
        }
        limit = Math.max(1, Math.min(1000, limit));
        RingLogHandler.Tail tail = tailSupplier.apply(limit);
        ctx.json(new LogsResponse(tail.lines().toArray(new String[0]), tail.lastSeq()));
    }
}
