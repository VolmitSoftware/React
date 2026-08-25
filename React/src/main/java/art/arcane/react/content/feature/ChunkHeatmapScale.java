package art.arcane.react.content.feature;

import java.util.Arrays;

final class ChunkHeatmapScale {
  private static final int PERCENTILE_SAMPLE_LIMIT = 1024;

  private final double maximum;
  private final boolean quiet;

  private ChunkHeatmapScale(double maximum, boolean quiet) {
    this.maximum = maximum;
    this.quiet = quiet;
  }

  static ChunkHeatmapScale fromValues(double[] values, double significantThreshold) {
    int positiveCount = positiveCount(values);
    double maximum = positiveCount == 0 ? 0D : percentile95(values, positiveCount);
    double threshold = Double.isFinite(significantThreshold) ? Math.max(0D, significantThreshold) : 0D;
    return new ChunkHeatmapScale(maximum, maximum < threshold);
  }

  double maximum() {
    return maximum;
  }

  boolean quiet() {
    return quiet;
  }

  double normalize(double value) {
    if (!Double.isFinite(value) || value <= 0D || maximum <= 0D) {
      return 0D;
    }
    return Math.max(0D, Math.min(1D, value / maximum));
  }

  int rampIndex(double value, int rampSize) {
    if (rampSize <= 1) {
      return 0;
    }
    return (int) Math.round(normalize(value) * (rampSize - 1));
  }

  private static int positiveCount(double[] values) {
    if (values == null || values.length == 0) {
      return 0;
    }
    int count = 0;
    for (double value : values) {
      if (Double.isFinite(value) && value > 0D) {
        count++;
      }
    }
    return count;
  }

  private static double percentile95(double[] values, int positiveCount) {
    int sampleSize = Math.min(positiveCount, PERCENTILE_SAMPLE_LIMIT);
    double[] sample = new double[sampleSize];
    int positiveIndex = 0;
    int sampleIndex = 0;
    for (double value : values) {
      if (!Double.isFinite(value) || value <= 0D) {
        continue;
      }
      if (((long) positiveIndex * sampleSize) / positiveCount == sampleIndex) {
        sample[sampleIndex] = value;
        sampleIndex++;
        if (sampleIndex == sampleSize) {
          break;
        }
      }
      positiveIndex++;
    }
    Arrays.sort(sample);
    int percentileIndex = Math.max(0, (int) Math.ceil(sample.length * 0.95D) - 1);
    return sample[percentileIndex];
  }
}
