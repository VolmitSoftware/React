package art.arcane.react.web;

import art.arcane.react.api.web.heatmap.HeatmapCellDto;
import art.arcane.react.api.web.heatmap.HeatmapDto;
import art.arcane.react.api.web.heatmap.HeatmapSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HeatmapSerializerTest {

    @Test
    void it_returns_zero_minmax_and_empty_cells_for_no_cells() {
        HeatmapSerializer serializer = new HeatmapSerializer();
        HeatmapDto dto = serializer.toDto("test-id", "Test Label", "world", 0, 0, 8, List.of());
        assertEquals("test-id", dto.id);
        assertEquals("Test Label", dto.label);
        assertEquals("world", dto.world);
        assertEquals(0, dto.centerChunkX);
        assertEquals(0, dto.centerChunkZ);
        assertEquals(8, dto.radius);
        assertEquals(0.0, dto.min, 1e-9);
        assertEquals(0.0, dto.max, 1e-9);
        assertEquals(0, dto.cells.length);
    }

    @Test
    void it_computes_min_and_max_over_scores_and_preserves_coords() {
        HeatmapSerializer serializer = new HeatmapSerializer();
        List<HeatmapCellDto> cells = List.of(
            new HeatmapCellDto(1, 2, 5.0),
            new HeatmapCellDto(3, 4, 10.0)
        );
        HeatmapDto dto = serializer.toDto("id", "label", "world", 10, 20, 5, cells);
        assertEquals(5.0, dto.min, 1e-9);
        assertEquals(10.0, dto.max, 1e-9);
        assertEquals(2, dto.cells.length);
        assertEquals(1, dto.cells[0].x);
        assertEquals(2, dto.cells[0].z);
        assertEquals(5.0, dto.cells[0].score, 1e-9);
        assertEquals(3, dto.cells[1].x);
        assertEquals(4, dto.cells[1].z);
        assertEquals(10.0, dto.cells[1].score, 1e-9);
    }
}
