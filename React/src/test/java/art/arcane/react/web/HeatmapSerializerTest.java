package art.arcane.react.web;

import art.arcane.react.api.web.heatmap.HeatmapCellDto;
import art.arcane.react.api.web.heatmap.HeatmapDto;
import art.arcane.react.api.web.heatmap.HeatmapScan;
import art.arcane.react.api.web.heatmap.HeatmapSerializer;
import art.arcane.react.api.web.heatmap.HeatmapViewportPlanner;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HeatmapSerializerTest {

    private final HeatmapViewportPlanner planner = new HeatmapViewportPlanner();

    @Test
    void it_returns_zero_scale_and_complete_viewport_metadata_for_no_cells() {
        HeatmapSerializer serializer = new HeatmapSerializer();
        HeatmapScan scan = new HeatmapScan(
            world(),
            planner.plan(0, 0, 8),
            1234L,
            List.of()
        );

        HeatmapDto dto = serializer.toDto("test-id", "Test Label", scan);

        assertEquals("test-id", dto.id);
        assertEquals("Test Label", dto.label);
        assertEquals("react-test:world", dto.world);
        assertEquals(0, dto.centerChunkX);
        assertEquals(0, dto.centerChunkZ);
        assertEquals(8, dto.radius);
        assertEquals(-8, dto.originChunkX);
        assertEquals(-8, dto.originChunkZ);
        assertEquals(17, dto.width);
        assertEquals(17, dto.height);
        assertEquals(1, dto.cellSizeChunks);
        assertEquals(1234L, dto.capturedAtMs);
        assertEquals(4, dto.spawnChunkX);
        assertEquals(-7, dto.spawnChunkZ);
        assertEquals(12.5D, dto.worldBorder.centerBlockX, 1e-9);
        assertEquals(-4.5D, dto.worldBorder.centerBlockZ, 1e-9);
        assertEquals(1_000D, dto.worldBorder.sizeBlocks, 1e-9);
        assertEquals(0D, dto.min, 1e-9);
        assertEquals(0D, dto.max, 1e-9);
        assertEquals(0, dto.cells.length);
    }

    @Test
    void it_uses_positive_p95_scale_without_clipping_cell_peaks() {
        HeatmapSerializer serializer = new HeatmapSerializer();
        List<HeatmapCellDto> cells = new ArrayList<>();
        for (int index = 0; index < 19; index++) {
            cells.add(new HeatmapCellDto(index, 0, 1, 10D, 10D, 1));
        }
        cells.add(new HeatmapCellDto(19, 0, 1, 1_000D, 1_000D, 1));
        HeatmapScan scan = new HeatmapScan(
            world(),
            planner.plan(0, 0, 16),
            5678L,
            cells
        );

        HeatmapDto dto = serializer.toDto("id", "label", scan);

        assertEquals(0D, dto.min, 1e-9);
        assertEquals(10D, dto.max, 1e-9);
        assertEquals(1_000D, dto.cells[19].score, 1e-9);
        assertEquals(1_000D, dto.cells[19].averageScore, 1e-9);
        assertEquals(1, dto.cells[19].samples);
    }

    private HeatmapWorldRef world() {
        return new HeatmapWorldRef(
            UUID.randomUUID(),
            "react-test:world",
            "world",
            4,
            -7,
            12.5D,
            -4.5D,
            1_000D
        );
    }
}
