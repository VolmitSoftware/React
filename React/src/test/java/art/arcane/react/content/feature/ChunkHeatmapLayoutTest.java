package art.arcane.react.content.feature;

import art.arcane.react.api.rendering.Region;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ChunkHeatmapLayoutTest {
  @Test
  void centersAnOddNorthUpGridAcrossNegativeChunkCoordinates() {
    ChunkHeatmapLayout layout = ChunkHeatmapLayout.create(
        new Region(0, 14, 128, 102),
        -1,
        -33,
        5,
        0,
        18,
        8
    );

    Assertions.assertEquals(21, layout.columns());
    Assertions.assertEquals(17, layout.rows());
    Assertions.assertEquals(-11, layout.minChunkX());
    Assertions.assertEquals(9, layout.maxChunkX());
    Assertions.assertEquals(-41, layout.minChunkZ());
    Assertions.assertEquals(-25, layout.maxChunkZ());
    Assertions.assertEquals(layout.grid().centerX(), layout.cell(-1, -33).centerX());
    Assertions.assertEquals(layout.grid().centerY(), layout.cell(-1, -33).centerY());
    Assertions.assertTrue(layout.cell(-1, -34).y() < layout.cell(-1, -33).y());
  }

  @Test
  void radiusCapProducesExactChunkAlignedBounds() {
    ChunkHeatmapLayout layout = ChunkHeatmapLayout.create(
        new Region(0, 0, 512, 512),
        -20,
        40,
        5,
        3,
        18,
        8
    );

    Assertions.assertEquals(7, layout.columns());
    Assertions.assertEquals(7, layout.rows());
    Assertions.assertEquals(-23, layout.minChunkX());
    Assertions.assertEquals(-17, layout.maxChunkX());
    Assertions.assertEquals(37, layout.minChunkZ());
    Assertions.assertEquals(43, layout.maxChunkZ());
    Assertions.assertEquals(0, layout.indexOf(-23, 37));
    Assertions.assertEquals(48, layout.indexOf(-17, 43));
    Assertions.assertEquals(-1, layout.indexOf(-24, 37));
  }

  @Test
  void mcaBoundariesUseFloorModForNegativeCoordinates() {
    Assertions.assertTrue(ChunkHeatmapLayout.startsMcaRegion(-32));
    Assertions.assertTrue(ChunkHeatmapLayout.endsMcaRegion(-1));
    Assertions.assertTrue(ChunkHeatmapLayout.startsMcaRegion(0));
    Assertions.assertTrue(ChunkHeatmapLayout.endsMcaRegion(31));
    Assertions.assertFalse(ChunkHeatmapLayout.startsMcaRegion(-1));
    Assertions.assertFalse(ChunkHeatmapLayout.endsMcaRegion(-32));
  }
}
