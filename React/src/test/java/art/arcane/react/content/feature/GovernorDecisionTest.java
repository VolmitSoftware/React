package art.arcane.react.content.feature;

import art.arcane.react.model.MinMax;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GovernorDecisionTest {

  @ParameterizedTest(name = "tickMs={0} incident={2} -> pressure={4}")
  @CsvSource({
      "60.0, 60.0, 0.0, 62.0, true",
      "80.0, 60.0, 0.0, 62.0, true",
      "10.0, 60.0, 62.0, 62.0, true",
      "59.999, 60.0, 61.999, 62.0, false"
  })
  void randomTickIsUnderPressureWhenEitherTickTimeOrIncidentReachesItsThreshold(double tickMs,
                                                                               double engageTickTimeMs,
                                                                               double incident,
                                                                               double engageIncidentScore,
                                                                               boolean expected) {
    Assertions.assertEquals(expected, FeatureRandomTickGovernor.isUnderPressure(tickMs, engageTickTimeMs, incident, engageIncidentScore));
  }

  @ParameterizedTest(name = "tickMs={0} incident={2} -> calm={4}")
  @CsvSource({
      "45.0, 45.0, 10.0, 62.0, true",
      "50.0, 45.0, 10.0, 62.0, false",
      "40.0, 45.0, 62.0, 62.0, false"
  })
  void randomTickIsCalmOnlyWhenTickTimeAtReleaseAndIncidentBelowEngageScore(double tickMs,
                                                                           double releaseTickTimeMs,
                                                                           double incident,
                                                                           double engageIncidentScore,
                                                                           boolean expected) {
    Assertions.assertEquals(expected, FeatureRandomTickGovernor.isCalm(tickMs, releaseTickTimeMs, incident, engageIncidentScore));
  }

  @ParameterizedTest(name = "base={0} factor={1} floor={2} -> {3}")
  @CsvSource({
      "64, 0.5, 16, 32",
      "20, 0.5, 16, 16",
      "64, 0.0, 16, 16",
      "3, 0.0, 0, 1"
  })
  void trackerScaledRangeScalesBaseThenClampsToAtLeastOneAndTheConfiguredFloor(int base,
                                                                              double factor,
                                                                              int minimumRangeBlocks,
                                                                              int expected) {
    Assertions.assertEquals(expected, FeatureTrackerRangeGovernor.scaledRange(base, factor, minimumRangeBlocks));
  }

  @ParameterizedTest(name = "inRange={0} -> {1}")
  @CsvSource({
      "45.0, 16.0",
      "140.0, 6.0",
      "92.5, 11.0",
      "0.0, 16.0",
      "200.0, 6.0"
  })
  void viewDistanceLerpInvertsLoadAcrossOutputRangeAndClampsOutsideTheInputRange(double inRange, double expected) {
    MinMax range = new MinMax(45.0, 140.0);
    MinMax output = new MinMax(6.0, 16.0);
    Assertions.assertEquals(expected, FeatureDynamicViewDistance.lerp(range, output, inRange), 1e-9);
  }

  @ParameterizedTest(name = "idleAfterSeconds={0} -> {1}ms")
  @CsvSource({
      "180, 180000",
      "5, 10000"
  })
  void afkIdleThresholdConvertsSecondsToMillisAboveTheTenSecondFloor(int idleAfterSeconds, long expected) {
    Assertions.assertEquals(expected, FeatureAfkViewShedding.idleThresholdMs(idleAfterSeconds));
  }

  @ParameterizedTest(name = "cap={0} -> {1}")
  @CsvSource({
      "1, 2",
      "8, 8"
  })
  void afkPressureCapClampsToTwoBlocksAndOtherwisePassesThrough(int pressureSendViewDistanceCap, int expected) {
    Assertions.assertEquals(expected, FeatureAfkViewShedding.pressureCap(pressureSendViewDistanceCap));
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
