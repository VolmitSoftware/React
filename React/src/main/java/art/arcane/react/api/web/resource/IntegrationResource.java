package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.IntegrationStatusDto;
import art.arcane.react.api.web.dto.IntegrationsDto;
import art.arcane.react.core.controller.IntegrationController;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class IntegrationResource {

    private final Supplier<List<IntegrationController.IntegrationStatus>> statusSupplier;
    private final IntFunction<List<String>> timelineSupplier;

    public IntegrationResource(
            Supplier<List<IntegrationController.IntegrationStatus>> statusSupplier,
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

        List<IntegrationController.IntegrationStatus> statuses = statusSupplier.get();
        List<String> timeline = timelineSupplier.apply(limit);

        IntegrationStatusDto[] integrationDtos = new IntegrationStatusDto[statuses.size()];
        for (int i = 0; i < statuses.size(); i++) {
            IntegrationController.IntegrationStatus status = statuses.get(i);
            IntegrationStatusDto dto = new IntegrationStatusDto();
            dto.pluginId = status.pluginId();
            dto.health = status.health().name();
            dto.protocol = status.protocol();
            dto.lastHeartbeatMs = status.lastHeartbeatMs();
            dto.message = status.message();
            integrationDtos[i] = dto;
        }

        IntegrationsDto result = new IntegrationsDto();
        result.integrations = integrationDtos;
        result.timeline = timeline.toArray(new String[0]);

        ctx.json(new Envelope<>(result));
    }
}
