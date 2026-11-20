package art.arcane.react.content.feature;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DynamicActivationRangeTuningTest {
  @ParameterizedTest(name = "range={0} tick={1} -> {2}")
  @CsvSource({
      "64, 80, 58",
      "20, 80, 18",
      "64, 50, 62",
      "19, 50, 18",
      "40, 30, 41",
      "64, 30, 64",
      "40, 45, 41",
      "40, 70, 38"
  })
  void shrinksFastPastCriticalSlowlyPastTargetAndRecoversOneBlockAtATime(double current, double tickMs, double expected) {
    Assertions.assertEquals(expected, FeatureDynamicActivationRange.tunedRange(current, tickMs, 45, 70, 18, 64), 1e-9);
  }

  @Property(tries = 200)
  void tunedRangeStaysWithinBounds(@ForAll @DoubleRange(min = 18, max = 64) double current,
                                   @ForAll @DoubleRange(min = 0, max = 500) double tickMs) {
    double tuned = FeatureDynamicActivationRange.tunedRange(current, tickMs, 45, 70, 18, 64);
    Assertions.assertTrue(tuned >= 18 && tuned <= 64);
  }
}
