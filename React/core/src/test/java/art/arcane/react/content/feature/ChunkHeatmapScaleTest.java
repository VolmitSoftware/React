package art.arcane.react.content.feature;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ChunkHeatmapScaleTest {
  @Test
  void anchorsAtZeroAndUsesPositiveP95() {
    double[] values = new double[103];
    values[0] = Double.NaN;
    values[1] = 0D;
    values[2] = -50D;
    for (int index = 1; index <= 100; index++) {
      values[index + 2] = index;
    }

    ChunkHeatmapScale scale = ChunkHeatmapScale.fromValues(values, 0.001D);

    Assertions.assertEquals(95D, scale.maximum());
    Assertions.assertEquals(0D, scale.normalize(0D));
    Assertions.assertEquals(0.5D, scale.normalize(47.5D));
    Assertions.assertEquals(1D, scale.normalize(10_000D));
    Assertions.assertFalse(scale.quiet());
  }

  @Test
  void ignoresNonFiniteValuesAndReportsQuietBelowThreshold() {
    ChunkHeatmapScale scale = ChunkHeatmapScale.fromValues(
        new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1D, 0D, 0.0005D},
        0.001D
    );

    Assertions.assertEquals(0.0005D, scale.maximum());
    Assertions.assertTrue(scale.quiet());
    Assertions.assertEquals(0, scale.rampIndex(Double.NaN, 64));
  }

  @Test
  void boundedSamplingStillRejectsAPathologicalMaximum() {
    double[] values = new double[4_001];
    for (int index = 0; index < 4_000; index++) {
      values[index] = 10D;
    }
    values[4_000] = 1_000_000D;

    ChunkHeatmapScale scale = ChunkHeatmapScale.fromValues(values, 0D);

    Assertions.assertEquals(10D, scale.maximum());
  }
}
