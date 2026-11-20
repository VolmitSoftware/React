package art.arcane.react.content.feature;

import art.arcane.react.api.rendering.Region;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class ChunkHeatmapRendererCacheTest {
  @Test
  void independentViewportsReuseTheirOwnScansUntilExpiry() {
    CountingHost host = new CountingHost();
    ChunkCostHeatmap heatmap = new ChunkCostHeatmap(host, MapAnchorHost.NONE, null);
    UUID world = UUID.randomUUID();
    ChunkHeatmapLayout first = layout(0, 0, 128);
    ChunkHeatmapLayout second = layout(64, 0, 128);
    ChunkHeatmapRenderer.ScanSnapshot firstScan = heatmap.scan(world, first, 1_000L);
    ChunkHeatmapRenderer.ScanSnapshot secondScan = heatmap.scan(world, second, 1_010L);
    Assertions.assertSame(firstScan, heatmap.scan(world, first, 1_100L));
    Assertions.assertSame(secondScan, heatmap.scan(world, second, 1_200L));
    Assertions.assertNotSame(firstScan, heatmap.scan(world, first, 1_300L));
    Assertions.assertEquals(3, host.scans.get());
  }

  @Test
  void worldChangesAndLifecycleInvalidationCannotReuseScans() {
    ChunkCostHeatmap heatmap = new ChunkCostHeatmap(new CountingHost(), MapAnchorHost.NONE, null);
    UUID world = UUID.randomUUID();
    ChunkHeatmapLayout layout = layout(0, 0, 128);
    ChunkHeatmapRenderer.ScanSnapshot first = heatmap.scan(world, layout, 1_000L);
    Assertions.assertNotSame(first, heatmap.scan(UUID.randomUUID(), layout, 1_001L));
    heatmap.clearCache();
    ChunkHeatmapRenderer.ScanSnapshot replacement = heatmap.scan(world, layout, 1_002L);
    Assertions.assertNotSame(first, replacement);
    ChunkHeatmapRenderer.ScanSnapshot afterClockChange = heatmap.scan(world, layout, 999L);
    Assertions.assertNotSame(replacement, afterClockChange);
    Assertions.assertSame(afterClockChange, heatmap.scan(world, layout, 1_000L));
  }

  @Test
  void olderScanCompletionKeepsNewerIndependentViewport() {
    CountingHost host = new CountingHost();
    ChunkCostHeatmap heatmap = new ChunkCostHeatmap(host, MapAnchorHost.NONE, null);
    UUID world = UUID.randomUUID();
    ChunkHeatmapLayout first = layout(0, 0, 128);
    ChunkHeatmapLayout second = layout(64, 0, 128);
    AtomicBoolean pending = new AtomicBoolean(true);
    AtomicReference<ChunkHeatmapRenderer.ScanSnapshot> newer = new AtomicReference<>();
    host.duringScan = () -> {
      if (pending.getAndSet(false)) {
        newer.set(heatmap.scan(world, second, 1_010L));
      }
    };
    ChunkHeatmapRenderer.ScanSnapshot older = heatmap.scan(world, first, 1_000L);
    Assertions.assertSame(newer.get(), heatmap.scan(world, second, 1_100L));
    Assertions.assertSame(older, heatmap.scan(world, first, 1_100L));
    Assertions.assertEquals(2, host.scans.get());
  }

  @Test
  void olderScanCompletionCannotReplaceNewerSameViewportAfterClockChange() {
    CountingHost host = new CountingHost();
    ChunkCostHeatmap heatmap = new ChunkCostHeatmap(host, MapAnchorHost.NONE, null);
    UUID world = UUID.randomUUID();
    ChunkHeatmapLayout layout = layout(0, 0, 128);
    AtomicBoolean pending = new AtomicBoolean(true);
    AtomicReference<ChunkHeatmapRenderer.ScanSnapshot> newer = new AtomicReference<>();
    host.duringScan = () -> {
      if (pending.getAndSet(false)) {
        newer.set(heatmap.scan(world, layout, 999L));
      }
    };
    ChunkHeatmapRenderer.ScanSnapshot older = heatmap.scan(world, layout, 1_000L);
    Assertions.assertNotSame(older, newer.get());
    Assertions.assertSame(newer.get(), heatmap.scan(world, layout, 1_100L));
    Assertions.assertEquals(2, host.scans.get());
  }

  @Test
  void cacheEvictsLeastRecentlyUsedViewportAtItsEntryLimit() {
    ChunkCostHeatmap heatmap = new ChunkCostHeatmap(new CountingHost(), MapAnchorHost.NONE, null);
    UUID world = UUID.randomUUID();
    ChunkHeatmapLayout first = layout(0, 0, 128);
    ChunkHeatmapLayout second = layout(1, 0, 128);
    ChunkHeatmapRenderer.ScanSnapshot retained = heatmap.scan(world, first, 1_000L);
    ChunkHeatmapRenderer.ScanSnapshot evicted = heatmap.scan(world, second, 1_000L);
    for (int index = 2; index < 16; index++) {
      heatmap.scan(world, layout(index, 0, 128), 1_000L);
    }
    Assertions.assertSame(retained, heatmap.scan(world, first, 1_010L));
    heatmap.scan(world, layout(16, 0, 128), 1_020L);
    Assertions.assertSame(retained, heatmap.scan(world, first, 1_030L));
    Assertions.assertNotSame(evicted, heatmap.scan(world, second, 1_040L));
  }

  @Test
  void oversizedViewportDoesNotConsumeTheCacheBudget() {
    ChunkCostHeatmap heatmap = new ChunkCostHeatmap(new CountingHost(), MapAnchorHost.NONE, null);
    UUID world = UUID.randomUUID();
    ChunkHeatmapLayout huge = layout(0, 0, 2_000);
    ChunkHeatmapRenderer.ScanSnapshot scan = heatmap.scan(world, huge, 1_000L);
    Assertions.assertNotSame(scan, heatmap.scan(world, huge, 1_001L));
  }

  private ChunkHeatmapLayout layout(int x, int z, int size) {
    return ChunkHeatmapLayout.create(new Region(0, 0, size, size), x, z, 5, 0, 0, 0);
  }

  private static final class CountingHost implements ChunkHeatmapHost {
    private final AtomicInteger scans = new AtomicInteger();
    private Runnable duringScan = () -> {
    };

    @Override
    public Optional<HeatmapWorldRef> heatmapWorld(UUID worldId) {
      scans.incrementAndGet();
      duringScan.run();
      return Optional.empty();
    }

    @Override
    public List<LoadedChunkCoordinate> loadedChunks(UUID worldId, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
      return List.of();
    }

    @Override
    public double chunkSample(String worldKey, int chunkX, int chunkZ, String samplerId) {
      return 0D;
    }

    @Override
    public double chunkTotalScore(String worldKey, int chunkX, int chunkZ) {
      return 0D;
    }

    @Override
    public List<PlayerPosition> playersInColumn(UUID worldId, double x, double z, double radiusBlocks) {
      return List.of();
    }
  }
}
