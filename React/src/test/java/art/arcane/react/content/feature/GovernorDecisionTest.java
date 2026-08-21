package art.arcane.react.content.feature;

import art.arcane.react.model.MinMax;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GovernorDecisionTest {

  @Test
  void randomTickPressureEngagesWhenTickTimeAtThreshold() {
    Assertions.assertTrue(FeatureRandomTickGovernor.isUnderPressure(60.0, 60.0, 0.0, 62.0));
  }

  @Test
  void randomTickPressureEngagesWhenTickTimeAboveThreshold() {
    Assertions.assertTrue(FeatureRandomTickGovernor.isUnderPressure(80.0, 60.0, 0.0, 62.0));
  }

  @Test
  void randomTickPressureEngagesWhenIncidentAtThreshold() {
    Assertions.assertTrue(FeatureRandomTickGovernor.isUnderPressure(10.0, 60.0, 62.0, 62.0));
  }

  @Test
  void randomTickPressureStaysCalmWhenBothBelowThresholds() {
    Assertions.assertFalse(FeatureRandomTickGovernor.isUnderPressure(59.999, 60.0, 61.999, 62.0));
  }

  @Test
  void randomTickCalmWhenTickAtReleaseAndIncidentBelowEngage() {
    Assertions.assertTrue(FeatureRandomTickGovernor.isCalm(45.0, 45.0, 10.0, 62.0));
  }

  @Test
  void randomTickNotCalmWhenTickAboveRelease() {
    Assertions.assertFalse(FeatureRandomTickGovernor.isCalm(50.0, 45.0, 10.0, 62.0));
  }

  @Test
  void randomTickNotCalmWhenIncidentAtEngageScore() {
    Assertions.assertFalse(FeatureRandomTickGovernor.isCalm(40.0, 45.0, 62.0, 62.0));
  }

  @Test
  void trackerScaledRangeHalvesAboveFloor() {
    Assertions.assertEquals(32, FeatureTrackerRangeGovernor.scaledRange(64, 0.5, 16));
  }

  @Test
  void trackerScaledRangeClampsToMinimumFloor() {
    Assertions.assertEquals(16, FeatureTrackerRangeGovernor.scaledRange(20, 0.5, 16));
  }

  @Test
  void trackerScaledRangeZeroFactorClampsToFloor() {
    Assertions.assertEquals(16, FeatureTrackerRangeGovernor.scaledRange(64, 0.0, 16));
  }

  @Test
  void trackerScaledRangeFloorAtLeastOneWhenMinimumZero() {
    Assertions.assertEquals(1, FeatureTrackerRangeGovernor.scaledRange(3, 0.0, 0));
  }

  @Test
  void viewDistanceLerpReturnsOutputMaxAtRangeMin() {
    MinMax range = new MinMax(45.0, 140.0);
    MinMax output = new MinMax(6.0, 16.0);
    Assertions.assertEquals(16.0, FeatureDynamicViewDistance.lerp(range, output, 45.0), 1e-9);
  }

  @Test
  void viewDistanceLerpReturnsOutputMinAtRangeMax() {
    MinMax range = new MinMax(45.0, 140.0);
    MinMax output = new MinMax(6.0, 16.0);
    Assertions.assertEquals(6.0, FeatureDynamicViewDistance.lerp(range, output, 140.0), 1e-9);
  }

  @Test
  void viewDistanceLerpReturnsMidpointAtRangeMidpoint() {
    MinMax range = new MinMax(45.0, 140.0);
    MinMax output = new MinMax(6.0, 16.0);
    Assertions.assertEquals(11.0, FeatureDynamicViewDistance.lerp(range, output, 92.5), 1e-9);
  }

  @Test
  void viewDistanceLerpClampsToMaxBelowRange() {
    MinMax range = new MinMax(45.0, 140.0);
    MinMax output = new MinMax(6.0, 16.0);
    Assertions.assertEquals(16.0, FeatureDynamicViewDistance.lerp(range, output, 0.0), 1e-9);
  }

  @Test
  void viewDistanceLerpClampsToMinAboveRange() {
    MinMax range = new MinMax(45.0, 140.0);
    MinMax output = new MinMax(6.0, 16.0);
    Assertions.assertEquals(6.0, FeatureDynamicViewDistance.lerp(range, output, 200.0), 1e-9);
  }

  @Test
  void afkIdleThresholdUsesConfiguredSeconds() {
    Assertions.assertEquals(180_000L, FeatureAfkViewShedding.idleThresholdMs(180));
  }

  @Test
  void afkIdleThresholdClampsToTenSecondFloor() {
    Assertions.assertEquals(10_000L, FeatureAfkViewShedding.idleThresholdMs(5));
  }

  @Test
  void afkPressureCapClampsToTwoBlocksFloor() {
    Assertions.assertEquals(2, FeatureAfkViewShedding.pressureCap(1));
  }

  @Test
  void afkPressureCapPassesThroughAboveFloor() {
    Assertions.assertEquals(8, FeatureAfkViewShedding.pressureCap(8));
  }

  @Property(tries = 200)
  void trackerScaledRangeNeverBelowFloor(@ForAll @IntRange(min = 1, max = 512) int base,
                                         @ForAll @DoubleRange(min = 0.0, max = 1.0) double factor,
                                         @ForAll @IntRange(min = 0, max = 64) int minimumRangeBlocks) {
    int result = FeatureTrackerRangeGovernor.scaledRange(base, factor, minimumRangeBlocks);
    Assertions.assertTrue(result >= Math.max(1, minimumRangeBlocks));
  }

  @Property(tries = 200)
  void trackerScaledRangeMonotonicNonDecreasingInBase(@ForAll @IntRange(min = 1, max = 512) int base,
                                                      @ForAll @IntRange(min = 0, max = 256) int delta,
                                                      @ForAll @DoubleRange(min = 0.0, max = 1.0) double factor,
                                                      @ForAll @IntRange(min = 0, max = 64) int minimumRangeBlocks) {
    int low = FeatureTrackerRangeGovernor.scaledRange(base, factor, minimumRangeBlocks);
    int high = FeatureTrackerRangeGovernor.scaledRange(base + delta, factor, minimumRangeBlocks);
    Assertions.assertTrue(high >= low);
  }

  @Property(tries = 200)
  void viewDistanceLerpStaysWithinOutputBounds(@ForAll @DoubleRange(min = -100.0, max = 300.0) double inRange,
                                               @ForAll @IntRange(min = 0, max = 32) int outputMin,
                                               @ForAll @IntRange(min = 0, max = 32) int outputSpan,
                                               @ForAll @IntRange(min = 0, max = 200) int rangeMin,
                                               @ForAll @IntRange(min = 1, max = 200) int rangeSpan) {
    MinMax output = new MinMax(outputMin, outputMin + outputSpan);
    MinMax range = new MinMax(rangeMin, rangeMin + rangeSpan);
    double result = FeatureDynamicViewDistance.lerp(range, output, inRange);
    Assertions.assertTrue(result >= outputMin - 1e-9 && result <= outputMin + outputSpan + 1e-9);
  }

  @Property(tries = 200)
  void viewDistanceLerpMonotonicNonIncreasingInLoad(@ForAll @DoubleRange(min = -50.0, max = 250.0) double inRange,
                                                    @ForAll @DoubleRange(min = 0.0, max = 100.0) double bump,
                                                    @ForAll @IntRange(min = 0, max = 32) int outputMin,
                                                    @ForAll @IntRange(min = 0, max = 32) int outputSpan,
                                                    @ForAll @IntRange(min = 0, max = 200) int rangeMin,
                                                    @ForAll @IntRange(min = 1, max = 200) int rangeSpan) {
    MinMax output = new MinMax(outputMin, outputMin + outputSpan);
    MinMax range = new MinMax(rangeMin, rangeMin + rangeSpan);
    double low = FeatureDynamicViewDistance.lerp(range, output, inRange);
    double high = FeatureDynamicViewDistance.lerp(range, output, inRange + bump);
    Assertions.assertTrue(high <= low + 1e-9);
  }

  @Property(tries = 200)
  void afkIdleThresholdNeverBelowTenSeconds(@ForAll @IntRange(min = 0, max = 86_400) int idleAfterSeconds) {
    Assertions.assertTrue(FeatureAfkViewShedding.idleThresholdMs(idleAfterSeconds) >= 10_000L);
  }

  @Property(tries = 200)
  void afkPressureCapNeverBelowTwo(@ForAll @IntRange(min = -100, max = 1000) int pressureSendViewDistanceCap) {
    Assertions.assertTrue(FeatureAfkViewShedding.pressureCap(pressureSendViewDistanceCap) >= 2);
  }
}
