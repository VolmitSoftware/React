package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.rendering.Region;
import art.arcane.react.core.controller.ObserverController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

class FeatureChunkHeatmapCacheTest {
  @Test
  void independentViewportsReuseTheirOwnScansUntilExpiry() {
    FeatureChunkSamplerMap feature = new FeatureChunkSamplerMap();
    UUID world = UUID.randomUUID();
    ChunkHeatmapLayout first = layout(0, 0, 128);
    ChunkHeatmapLayout second = layout(64, 0, 128);
    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      FeatureChunkHeatmapBase.ScanSnapshot firstScan = feature.scan(world, first, 1_000L);
      FeatureChunkHeatmapBase.ScanSnapshot secondScan = feature.scan(world, second, 1_010L);
      Assertions.assertSame(firstScan, feature.scan(world, first, 1_100L));
      Assertions.assertSame(secondScan, feature.scan(world, second, 1_200L));
      Assertions.assertNotSame(firstScan, feature.scan(world, first, 1_300L));
      react.verify(() -> React.controller(ObserverController.class), Mockito.times(3));
    }
  }

  @Test
  void worldChangesAndLifecycleInvalidationCannotReuseScans() {
    FeatureChunkSamplerMap feature = new FeatureChunkSamplerMap();
    UUID world = UUID.randomUUID();
    ChunkHeatmapLayout layout = layout(0, 0, 128);
    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      FeatureChunkHeatmapBase.ScanSnapshot first = feature.scan(world, layout, 1_000L);
      Assertions.assertNotSame(first, feature.scan(UUID.randomUUID(), layout, 1_001L));
      feature.onDeactivate();
      FeatureChunkHeatmapBase.ScanSnapshot replacement = feature.scan(world, layout, 1_002L);
      Assertions.assertNotSame(first, replacement);
      Assertions.assertNotSame(replacement, feature.scan(world, layout, 999L));
    }
  }

  @Test
  void cacheEvictsLeastRecentlyUsedViewportAtItsEntryLimit() {
    FeatureChunkSamplerMap feature = new FeatureChunkSamplerMap();
    UUID world = UUID.randomUUID();
    ChunkHeatmapLayout first = layout(0, 0, 128);
    ChunkHeatmapLayout second = layout(1, 0, 128);
    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      FeatureChunkHeatmapBase.ScanSnapshot retained = feature.scan(world, first, 1_000L);
      FeatureChunkHeatmapBase.ScanSnapshot evicted = feature.scan(world, second, 1_000L);
      for (int index = 2; index < 16; index++) {
        feature.scan(world, layout(index, 0, 128), 1_000L);
      }
      Assertions.assertSame(retained, feature.scan(world, first, 1_010L));
      feature.scan(world, layout(16, 0, 128), 1_020L);
      Assertions.assertSame(retained, feature.scan(world, first, 1_030L));
      Assertions.assertNotSame(evicted, feature.scan(world, second, 1_040L));
    }
  }

  @Test
  void oversizedViewportDoesNotConsumeTheCacheBudget() {
    FeatureChunkSamplerMap feature = new FeatureChunkSamplerMap();
    UUID world = UUID.randomUUID();
    ChunkHeatmapLayout huge = layout(0, 0, 2_000);
    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      FeatureChunkHeatmapBase.ScanSnapshot scan = feature.scan(world, huge, 1_000L);
      Assertions.assertNotSame(scan, feature.scan(world, huge, 1_001L));
    }
  }

  private ChunkHeatmapLayout layout(int x, int z, int size) {
    return ChunkHeatmapLayout.create(new Region(0, 0, size, size), x, z, 5, 0, 0, 0);
  }
}
