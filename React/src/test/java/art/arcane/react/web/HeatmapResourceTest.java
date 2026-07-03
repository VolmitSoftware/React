package art.arcane.react.web;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.heatmap.HeatmapCellProvider;
import art.arcane.react.api.web.heatmap.HeatmapDto;
import art.arcane.react.api.web.heatmap.HeatmapSummaryDto;
import art.arcane.react.api.web.resource.HeatmapResource;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HeatmapResourceTest {

    @Test
    void it_list_returns_summaries() {
        HeatmapCellProvider provider = mock(HeatmapCellProvider.class);
        when(provider.summaries()).thenReturn(List.of(
            new HeatmapSummaryDto("entity-pressure-heatmap", "Entity Pressure"),
            new HeatmapSummaryDto("redstone-density-heatmap", "Redstone Density")
        ));

        HeatmapResource resource = new HeatmapResource(provider);
        Context ctx = mock(Context.class);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        resource.list(ctx);
        verify(ctx).json(captor.capture());

        HeatmapResource.ListResponse response = (HeatmapResource.ListResponse) captor.getValue();
        assertEquals(2, response.data().length);
    }

    @Test
    void it_detail_returns_envelope_for_known_id() {
        HeatmapCellProvider provider = mock(HeatmapCellProvider.class);
        HeatmapDto fixedDto = new HeatmapDto();
        fixedDto.id = "entity-pressure-heatmap";
        fixedDto.label = "Entity Pressure";
        fixedDto.world = "world";
        fixedDto.centerChunkX = 0;
        fixedDto.centerChunkZ = 0;
        fixedDto.radius = 8;
        fixedDto.min = 0.0;
        fixedDto.max = 42.0;
        fixedDto.cells = new art.arcane.react.api.web.heatmap.HeatmapCellDto[0];

        when(provider.compute("entity-pressure-heatmap", null, null, null, null)).thenReturn(fixedDto);

        HeatmapResource resource = new HeatmapResource(provider);
        Context ctx = mock(Context.class);
        when(ctx.pathParam("id")).thenReturn("entity-pressure-heatmap");
        when(ctx.queryParam("world")).thenReturn(null);
        when(ctx.queryParam("centerX")).thenReturn(null);
        when(ctx.queryParam("centerZ")).thenReturn(null);
        when(ctx.queryParam("radius")).thenReturn(null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.detail(ctx);
        verify(ctx).json(captor.capture());

        @SuppressWarnings("unchecked")
        Envelope<HeatmapDto> envelope = (Envelope<HeatmapDto>) captor.getValue();
        assertEquals(fixedDto, envelope.data());
    }

    @Test
    void it_detail_404_for_unknown_id() {
        HeatmapCellProvider provider = mock(HeatmapCellProvider.class);
        when(provider.compute("unknown-id", null, null, null, null)).thenReturn(null);

        HeatmapResource resource = new HeatmapResource(provider);
        Context ctx = mock(Context.class);
        when(ctx.pathParam("id")).thenReturn("unknown-id");
        when(ctx.queryParam("world")).thenReturn(null);
        when(ctx.queryParam("centerX")).thenReturn(null);
        when(ctx.queryParam("centerZ")).thenReturn(null);
        when(ctx.queryParam("radius")).thenReturn(null);

        assertThrows(NotFoundResponse.class, () -> resource.detail(ctx));
    }

    @Test
    void it_detail_400_for_non_numeric_radius() {
        HeatmapCellProvider provider = mock(HeatmapCellProvider.class);

        HeatmapResource resource = new HeatmapResource(provider);
        Context ctx = mock(Context.class);
        when(ctx.pathParam("id")).thenReturn("entity-pressure-heatmap");
        when(ctx.queryParam("world")).thenReturn(null);
        when(ctx.queryParam("centerX")).thenReturn(null);
        when(ctx.queryParam("centerZ")).thenReturn(null);
        when(ctx.queryParam("radius")).thenReturn("abc");

        assertThrows(BadRequestResponse.class, () -> resource.detail(ctx));
    }
}
