package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.heatmap.HeatmapCellProvider;
import art.arcane.react.api.web.heatmap.HeatmapDto;
import art.arcane.react.api.web.heatmap.HeatmapSummaryDto;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

public class HeatmapResource {

    private final HeatmapCellProvider provider;

    public HeatmapResource(HeatmapCellProvider provider) {
        this.provider = provider;
    }

    public void list(Context ctx) {
        ctx.json(new ListResponse(provider.summaries().toArray(new HeatmapSummaryDto[0])));
    }

    public void detail(Context ctx) {
        String id = ctx.pathParam("id");
        String world = ctx.queryParam("world");
        String centerXParam = ctx.queryParam("centerX");
        String centerZParam = ctx.queryParam("centerZ");
        String radiusParam = ctx.queryParam("radius");
        Integer centerX = null;
        Integer centerZ = null;
        Integer radius = null;
        try {
            if (centerXParam != null) {
                centerX = Integer.parseInt(centerXParam);
            }
            if (centerZParam != null) {
                centerZ = Integer.parseInt(centerZParam);
            }
            if (radiusParam != null) {
                radius = Integer.parseInt(radiusParam);
            }
        } catch (NumberFormatException e) {
            throw new BadRequestResponse();
        }
        HeatmapDto dto = provider.compute(id, world, centerX, centerZ, radius);
        if (dto == null) {
            throw new NotFoundResponse();
        }
        ctx.json(new Envelope<>(dto));
    }

    public record ListResponse(HeatmapSummaryDto[] data) {}
}
