package art.arcane.react.api.benchmark;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BenchmarkScaleTest {
  @Test
  public void referenceMeasurementScoresExactlyOneHundred() {
    Assertions.assertEquals(100, BenchmarkScale.higherIsBetter(190.0, BenchmarkScale.CPU_INTEGER_MOPS));
    Assertions.assertEquals(100, BenchmarkScale.lowerIsBetter(5.5, BenchmarkScale.DRIVE_FLUSH_MILLIS));
  }

  @Test
  public void higherIsBetterRewardsLargerMeasurements() {
    Assertions.assertEquals(200, BenchmarkScale.higherIsBetter(100.0, 50.0));
    Assertions.assertEquals(50, BenchmarkScale.higherIsBetter(25.0, 50.0));
    Assertions.assertTrue(
        BenchmarkScale.higherIsBetter(80.0, 50.0) > BenchmarkScale.higherIsBetter(60.0, 50.0)
    );
  }

  @Test
  public void lowerIsBetterRewardsSmallerMeasurements() {
    Assertions.assertEquals(200, BenchmarkScale.lowerIsBetter(25.0, 50.0));
    Assertions.assertEquals(50, BenchmarkScale.lowerIsBetter(100.0, 50.0));
    Assertions.assertTrue(
        BenchmarkScale.lowerIsBetter(10.0, 50.0) > BenchmarkScale.lowerIsBetter(40.0, 50.0)
    );
  }

  @Test
  public void nonPositiveAndNonFiniteMeasurementsScoreZero() {
    Assertions.assertEquals(0, BenchmarkScale.higherIsBetter(0.0, 50.0));
    Assertions.assertEquals(0, BenchmarkScale.higherIsBetter(-5.0, 50.0));
    Assertions.assertEquals(0, BenchmarkScale.higherIsBetter(Double.NaN, 50.0));
    Assertions.assertEquals(0, BenchmarkScale.higherIsBetter(Double.POSITIVE_INFINITY, 50.0));
    Assertions.assertEquals(0, BenchmarkScale.lowerIsBetter(0.0, 50.0));
    Assertions.assertEquals(0, BenchmarkScale.lowerIsBetter(Double.NaN, 50.0));
  }

  @Test
  public void scoresClampToTheMaximum() {
    Assertions.assertEquals(BenchmarkScale.MAXIMUM_SCORE, BenchmarkScale.higherIsBetter(1_000_000.0, 1.0));
    Assertions.assertEquals(BenchmarkScale.MAXIMUM_SCORE, BenchmarkScale.lowerIsBetter(0.000001, 1.0));
  }

  @Test
  public void blendAveragesComponentScores() {
    Assertions.assertEquals(100, BenchmarkScale.blend(50, 150));
    Assertions.assertEquals(75, BenchmarkScale.blend(50, 100, 75, 75));
    Assertions.assertEquals(0, BenchmarkScale.blend());
  }
}
