package art.arcane.react.content.feature;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdaptiveSleepDecisionTest {

  @ParameterizedTest(name = "load={0} threshold={1} slots={2} entity={3} -> doze={4}")
  @CsvSource({
      "10.0, 42.0, 4, 1, false",
      "50.0, 42.0, 4, 0, false",
      "50.0, 42.0, 4, 1, true",
      "50.0, 42.0, 1, 0, false",
      "50.0, 42.0, 1, 1, true",
      "50.0, 42.0, 4, -1, true",
      "42.0, 42.0, 4, 0, false",
      "42.0, 42.0, 4, 1, true"
  })
  void dozesOnlyOffTheAwakeSlotOnceLoadReachesTheThreshold(double lastTickMs,
                                                           double minTickMs,
                                                           int slots,
                                                           int entityId,
                                                           boolean expected) {
    Assertions.assertEquals(expected, FeatureAdaptiveEntitySleep.shouldDoze(lastTickMs, minTickMs, slots, entityId, 0));
  }

  @Property(tries = 200)
  void neverDozesWhenLoadBelowThreshold(@ForAll @DoubleRange(min = 0.0, max = 1000.0) double minTick,
                                        @ForAll @DoubleRange(min = 0.0, max = 1000.0) double deficit,
                                        @ForAll @IntRange(min = 1, max = 16) int slots,
                                        @ForAll int entityId,
                                        @ForAll int dutyCycleIndex) {
    double lastTickMs = minTick - deficit - 1e-6;
    Assertions.assertFalse(FeatureAdaptiveEntitySleep.shouldDoze(lastTickMs, minTick, slots, entityId, dutyCycleIndex));
  }

  @Property(tries = 200)
  void dozeImpliesLoadAtOrAboveThreshold(@ForAll @DoubleRange(min = 0.0, max = 500.0) double lastTickMs,
                                         @ForAll @DoubleRange(min = 0.0, max = 500.0) double minTick,
                                         @ForAll @IntRange(min = 1, max = 16) int slots,
                                         @ForAll int entityId,
                                         @ForAll int dutyCycleIndex) {
    if (FeatureAdaptiveEntitySleep.shouldDoze(lastTickMs, minTick, slots, entityId, dutyCycleIndex)) {
      Assertions.assertTrue(lastTickMs >= minTick);
    }
  }

  @Property(tries = 200)
  void exactlyOneAwakeSlotPerRotation(@ForAll @IntRange(min = 0, max = 100_000) int entityId,
                                      @ForAll @IntRange(min = 2, max = 16) int slots,
                                      @ForAll @DoubleRange(min = 42.0, max = 1000.0) double lastTickMs) {
    int awake = 0;
    for (int index = 0; index < slots; index++) {
      if (!FeatureAdaptiveEntitySleep.shouldDoze(lastTickMs, 42.0, slots, entityId, index)) {
        awake++;
      }
    }
    Assertions.assertEquals(1, awake);
  }
}
