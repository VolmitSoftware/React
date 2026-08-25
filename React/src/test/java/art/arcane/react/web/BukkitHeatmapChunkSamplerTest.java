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
        HeatmapWorldRef world = new HeatmapWorldRef(
            worldId,
            "react-test:world",
            "world",
            0,
            0,
            0D,
            0D,
            60_000_000D
        );
        ObserverController observer = mock(ObserverController.class);
        when(observer.heatmapWorld("react-test:world")).thenReturn(Optional.of(world));
        when(observer.loadedChunkCoordinatesInBounds(worldId, -12, 20, -19, 13)).thenReturn(List.of(
            new ObserverController.LoadedChunkCoordinate(-12, -19),
            new ObserverController.LoadedChunkCoordinate(20, 13),
            new ObserverController.LoadedChunkCoordinate(5, -3)
        ));
        ChunkGridExporter exporter = mock(ChunkGridExporter.class);
        when(exporter.scoreChunk(world, -12, -19)).thenReturn(2.5D);
        when(exporter.scoreChunk(world, 20, 13)).thenReturn(4D);
        when(exporter.scoreChunk(world, 5, -3)).thenReturn(0D);
        BukkitHeatmapChunkSampler sampler = new BukkitHeatmapChunkSampler(() -> observer);

        HeatmapScan result;
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            result = sampler.scan(exporter, "react-test:world", 4, -3, 16);
            bukkit.verifyNoInteractions();
        }

        assertEquals("react-test:world", result.world().worldKey());
        assertEquals(3, result.cells().size());
        assertEquals(-12, result.cells().get(0).x);
        assertEquals(-19, result.cells().get(0).z);
        assertEquals(0D, result.cells().get(1).score, 1e-9);
        assertEquals(1, result.cells().get(1).samples);
        assertEquals(20, result.cells().get(2).x);
        assertEquals(13, result.cells().get(2).z);
        org.mockito.Mockito.verify(observer).loadedChunkCoordinatesInBounds(worldId, -12, 20, -19, 13);
    }

    @Test
    void radius_seventeen_aggregates_loaded_chunks_into_aligned_buckets() {
        UUID worldId = UUID.randomUUID();
        HeatmapWorldRef world = new HeatmapWorldRef(
            worldId,
            "react-test:world",
            "world",
            0,
            0,
            0D,
            0D,
            60_000_000D
        );
        ObserverController observer = mock(ObserverController.class);
        when(observer.heatmapWorld("react-test:world")).thenReturn(Optional.of(world));
        when(observer.loadedChunkCoordinatesInBounds(worldId, -18, 17, -18, 17)).thenReturn(List.of(
            new ObserverController.LoadedChunkCoordinate(-18, -18),
            new ObserverController.LoadedChunkCoordinate(-17, -17)
        ));
        ChunkGridExporter exporter = mock(ChunkGridExporter.class);
        when(exporter.scoreChunk(world, -18, -18)).thenReturn(2D);
        when(exporter.scoreChunk(world, -17, -17)).thenReturn(6D);
        BukkitHeatmapChunkSampler sampler = new BukkitHeatmapChunkSampler(() -> observer);

        HeatmapScan result = sampler.scan(exporter, "react-test:world", 0, 0, 17);

        assertEquals(2, result.viewport().cellSizeChunks());
        assertEquals(-18, result.viewport().originChunkX());
        assertEquals(1, result.cells().size());
        assertEquals(-18, result.cells().get(0).x);
        assertEquals(-18, result.cells().get(0).z);
        assertEquals(2, result.cells().get(0).sizeChunks);
        assertEquals(6D, result.cells().get(0).score, 1e-9);
        assertEquals(4D, result.cells().get(0).averageScore, 1e-9);
        assertEquals(2, result.cells().get(0).samples);
    }
}
