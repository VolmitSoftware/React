package art.arcane.react.web;

import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.api.web.heatmap.HeatmapCellDto;
import art.arcane.react.api.web.heatmap.HeatmapChunkSampler;
import art.arcane.react.api.web.heatmap.HeatmapDto;
import art.arcane.react.api.web.heatmap.HeatmapScan;
import art.arcane.react.api.web.heatmap.HeatmapSerializer;
import art.arcane.react.api.web.heatmap.HeatmapSummaryDto;
import art.arcane.react.api.web.heatmap.ReactHeatmapCellProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ReactHeatmapCellProviderTest {

    private static ChunkGridExporter makeExporter(String id, String label) {
        return new ChunkGridExporter() {
            @Override
            public String heatmapId() {
                return id;
            }

            @Override
            public String heatmapLabel() {
                return label;
            }

            @Override
            public double scoreChunk(HeatmapWorldRef world, int chunkX, int chunkZ) {
                return 0D;
            }
        };
    }

    @Test
    void it_summaries_map_each_exporter() {
        ChunkGridExporter a = makeExporter("entity-pressure", "Entity Pressure");
        ChunkGridExporter b = makeExporter("redstone-activity", "Redstone Activity");
        ReactHeatmapCellProvider provider = new ReactHeatmapCellProvider(
            () -> List.of(a, b),
            (HeatmapChunkSampler) (exp, world, cx, cz, r) -> null,
            new HeatmapSerializer(),
            8, 16
        );
        List<HeatmapSummaryDto> summaries = provider.summaries();
        assertEquals(2, summaries.size());
        assertEquals("entity-pressure", summaries.get(0).id());
        assertEquals("Entity Pressure", summaries.get(0).label());
        assertEquals("redstone-activity", summaries.get(1).id());
        assertEquals("Redstone Activity", summaries.get(1).label());
    }

    @Test
    void it_compute_unknown_id_returns_null() {
        ReactHeatmapCellProvider provider = new ReactHeatmapCellProvider(
            () -> List.of(makeExporter("known-id", "Known")),
            (HeatmapChunkSampler) (exp, world, cx, cz, r) -> null,
            new HeatmapSerializer(),
            8, 16
        );
        assertNull(provider.compute("unknown-id", null, null, null, null));
    }

    @Test
    void it_compute_known_id_assembles_dto_with_minmax() {
        ChunkGridExporter exporter = makeExporter("entity-pressure", "Entity Pressure");
        HeatmapScan fixedScan = new HeatmapScan("w", 2, 3, List.of(
            new HeatmapCellDto(0, 0, 3.0),
            new HeatmapCellDto(1, 0, 7.0)
        ));
        ReactHeatmapCellProvider provider = new ReactHeatmapCellProvider(
            () -> List.of(exporter),
            (HeatmapChunkSampler) (exp, world, cx, cz, r) -> fixedScan,
            new HeatmapSerializer(),
            8, 16
        );
        HeatmapDto dto = provider.compute("entity-pressure", "w", 2, 3, 8);
        assertNotNull(dto);
        assertEquals("entity-pressure", dto.id);
        assertEquals("Entity Pressure", dto.label);
        assertEquals("w", dto.world);
        assertEquals(2, dto.centerChunkX);
        assertEquals(3, dto.centerChunkZ);
        assertEquals(3.0, dto.min, 0.0001);
        assertEquals(7.0, dto.max, 0.0001);
        assertEquals(2, dto.cells.length);
    }

    @Test
    void it_compute_clamps_radius() {
        ChunkGridExporter exporter = makeExporter("entity-pressure", "Entity Pressure");
        AtomicInteger lastRadius = new AtomicInteger(-1);
        HeatmapScan scanFor = new HeatmapScan("w", 0, 0, List.of());
        ReactHeatmapCellProvider provider = new ReactHeatmapCellProvider(
            () -> List.of(exporter),
            (HeatmapChunkSampler) (exp, world, cx, cz, r) -> {
                lastRadius.set(r);
                return scanFor;
            },
            new HeatmapSerializer(),
            8, 16
        );

        HeatmapDto dtoOver = provider.compute("entity-pressure", null, null, null, 999);
        assertEquals(16, lastRadius.get());
        assertEquals(16, dtoOver.radius);

        HeatmapDto dtoNull = provider.compute("entity-pressure", null, null, null, null);
        assertEquals(8, lastRadius.get());
        assertEquals(8, dtoNull.radius);

        HeatmapDto dtoZero = provider.compute("entity-pressure", null, null, null, 0);
        assertEquals(1, lastRadius.get());
        assertEquals(1, dtoZero.radius);
    }

    @Test
    void it_compute_null_scan_yields_empty_cells() {
        ChunkGridExporter exporter = makeExporter("entity-pressure", "Entity Pressure");
        ReactHeatmapCellProvider provider = new ReactHeatmapCellProvider(
            () -> List.of(exporter),
            (HeatmapChunkSampler) (exp, world, cx, cz, r) -> null,
            new HeatmapSerializer(),
            8, 16
        );
        HeatmapDto dto = provider.compute("entity-pressure", null, null, null, 5);
        assertNotNull(dto);
        assertEquals(0, dto.cells.length);
        assertEquals(0.0, dto.min, 0.0001);
        assertEquals(0.0, dto.max, 0.0001);
        assertEquals(5, dto.radius);
    }
}
