package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.WebAuth;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

import java.util.List;
import java.util.function.IntFunction;

public class LogsResource {

    private final IntFunction<List<String>> linesSupplier;

    public LogsResource(IntFunction<List<String>> linesSupplier) {
        this.linesSupplier = linesSupplier;
    }

    public record LogsResponse(String[] data) {}

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
        ctx.json(new LogsResponse(linesSupplier.apply(limit).toArray(new String[0])));
    }
}
