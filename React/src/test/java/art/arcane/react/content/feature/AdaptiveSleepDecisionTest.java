package art.arcane.react.content.feature;

import art.arcane.react.React;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdaptiveSleepDecisionTest {

  static {
    if (React.instance == null) {
      React react = Mockito.mock(React.class);
      Mockito.when(react.getName()).thenReturn("react");
      React.instance = react;
    }
  }

  @Test
  void neverDozesBelowLoadThreshold() {
    Assertions.assertFalse(FeatureAdaptiveEntitySleep.shouldDoze(10.0, 42.0, 4, 1, 0));
  }

  @Test
  void awakeSlotDoesNotDoze() {
    Assertions.assertFalse(FeatureAdaptiveEntitySleep.shouldDoze(50.0, 42.0, 4, 0, 0));
  }

  @Test
  void nonAwakeSlotDozesUnderLoad() {
    Assertions.assertTrue(FeatureAdaptiveEntitySleep.shouldDoze(50.0, 42.0, 4, 1, 0));
  }

  @Test
  void slotCountClampedToTwoSplitsAwakeAndDoze() {
    Assertions.assertFalse(FeatureAdaptiveEntitySleep.shouldDoze(50.0, 42.0, 1, 0, 0));
    Assertions.assertTrue(FeatureAdaptiveEntitySleep.shouldDoze(50.0, 42.0, 1, 1, 0));
  }

  @Test
  void negativeEntityIdUsesFloorModResidue() {
    Assertions.assertTrue(FeatureAdaptiveEntitySleep.shouldDoze(50.0, 42.0, 4, -1, 0));
  }

  @Test
  void loadAtThresholdEngagesSlotDecision() {
    Assertions.assertFalse(FeatureAdaptiveEntitySleep.shouldDoze(42.0, 42.0, 4, 0, 0));
    Assertions.assertTrue(FeatureAdaptiveEntitySleep.shouldDoze(42.0, 42.0, 4, 1, 0));
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
