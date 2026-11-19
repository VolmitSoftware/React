package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.IntegrationStatusDto;
import art.arcane.react.api.web.dto.IntegrationsDto;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class IntegrationResource {

    private final Supplier<List<IntegrationStatusDto>> statusSupplier;
    private final IntFunction<List<String>> timelineSupplier;

    public IntegrationResource(
            Supplier<List<IntegrationStatusDto>> statusSupplier,
            IntFunction<List<String>> timelineSupplier) {
        this.statusSupplier = statusSupplier;
        this.timelineSupplier = timelineSupplier;
    }

    public void get(Context ctx) {
        String limitParam = ctx.queryParam("limit");
        int limit;
        if (limitParam == null) {
            limit = 20;
        } else {
            try {
                int parsed = Integer.parseInt(limitParam);
                limit = Math.max(1, Math.min(200, parsed));
            } catch (NumberFormatException e) {
                throw new BadRequestResponse();
            }
        }

        List<IntegrationStatusDto> statuses = statusSupplier.get();
        List<String> timeline = timelineSupplier.apply(limit);

        IntegrationsDto result = new IntegrationsDto();
        result.integrations = statuses.toArray(new IntegrationStatusDto[0]);
        result.timeline = timeline.toArray(new String[0]);

        ctx.json(new Envelope<>(result));
    }
}
