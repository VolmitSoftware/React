package art.arcane.react.web;

import art.arcane.react.api.web.heatmap.BukkitHeatmapChunkSampler;
import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapScan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

public class BukkitHeatmapChunkSamplerTest {
    @Test
    void scan_returns_null_for_unqualified_world_key() {
        BukkitHeatmapChunkSampler sampler = new BukkitHeatmapChunkSampler();
        ChunkGridExporter exporter = mock(ChunkGridExporter.class);

        HeatmapScan result = sampler.scan(exporter, "world", null, null, 8);

        assertNull(result);
    }
}
