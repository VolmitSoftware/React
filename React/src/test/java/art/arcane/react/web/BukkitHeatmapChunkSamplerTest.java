package art.arcane.react.web;

import art.arcane.react.api.web.heatmap.BukkitHeatmapChunkSampler;
import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapScan;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.core.controller.ObserverController;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BukkitHeatmapChunkSamplerTest {
    @Test
    void scan_returns_null_for_unqualified_world_key() {
        ObserverController observer = mock(ObserverController.class);
        when(observer.heatmapWorld("world")).thenReturn(Optional.empty());
        BukkitHeatmapChunkSampler sampler = new BukkitHeatmapChunkSampler(() -> observer);
        ChunkGridExporter exporter = mock(ChunkGridExporter.class);

        HeatmapScan result = sampler.scan(exporter, "world", null, null, 8);

        assertNull(result);
    }

    @Test
    void scan_uses_bounded_coordinate_index_without_enumerating_world_chunks() {
        UUID worldId = UUID.randomUUID();
        HeatmapWorldRef world = new HeatmapWorldRef(worldId, "react-test:world", "world", 0, 0);
        ObserverController observer = mock(ObserverController.class);
        when(observer.heatmapWorld("react-test:world")).thenReturn(Optional.of(world));
        when(observer.loadedChunkCoordinatesInRadius(worldId, 4, -3, 8)).thenReturn(List.of(
            new ObserverController.LoadedChunkCoordinate(4, -3),
            new ObserverController.LoadedChunkCoordinate(5, -3)
        ));
        ChunkGridExporter exporter = mock(ChunkGridExporter.class);
        when(exporter.scoreChunk(world, 4, -3)).thenReturn(2.5D);
        when(exporter.scoreChunk(world, 5, -3)).thenReturn(0D);
        BukkitHeatmapChunkSampler sampler = new BukkitHeatmapChunkSampler(() -> observer);

        HeatmapScan result;
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            result = sampler.scan(exporter, "react-test:world", 4, -3, 8);
            bukkit.verifyNoInteractions();
        }

        assertEquals("react-test:world", result.world());
        assertEquals(1, result.cells().size());
        assertEquals(4, result.cells().get(0).x);
        assertEquals(-3, result.cells().get(0).z);
        org.mockito.Mockito.verify(observer).loadedChunkCoordinatesInRadius(worldId, 4, -3, 8);
    }
}
