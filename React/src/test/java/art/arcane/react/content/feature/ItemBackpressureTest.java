package art.arcane.react.content.feature;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ItemBackpressureTest {

  @Test
  void throttlesWhenTickTimeOverThreshold() {
    Assertions.assertTrue(FeatureItemBackpressure.shouldThrottle(70.0, 60.0, 0.0, 5000.0));
  }

  @Test
  void throttlesWhenEntityCountOverThreshold() {
    Assertions.assertTrue(FeatureItemBackpressure.shouldThrottle(10.0, 60.0, 6000.0, 5000.0));
  }

  @Test
  void doesNotThrottleWhenBothBelowThresholds() {
    Assertions.assertFalse(FeatureItemBackpressure.shouldThrottle(10.0, 60.0, 100.0, 5000.0));
  }

  @Test
  void throttlesWhenTickTimeAtThreshold() {
    Assertions.assertTrue(FeatureItemBackpressure.shouldThrottle(60.0, 60.0, 0.0, 5000.0));
  }

  @Test
  void throttlesWhenEntityCountAtThreshold() {
    Assertions.assertTrue(FeatureItemBackpressure.shouldThrottle(10.0, 60.0, 5000.0, 5000.0));
  }

  @Property(tries = 200)
  void doesNotThrottleWhenBothStrictlyBelow(@ForAll @DoubleRange(min = 1.0, max = 10_000.0) double triggerTickTimeMs,
                                            @ForAll @DoubleRange(min = 1.0, max = 1_000_000.0) double triggerEntityCount,
                                            @ForAll @DoubleRange(min = 0.0, max = 10_000.0) double tickGap,
                                            @ForAll @DoubleRange(min = 0.0, max = 1_000_000.0) double countGap) {
    double tickTimeMs = triggerTickTimeMs - 1e-3 - tickGap;
    double entityCount = triggerEntityCount - 1e-3 - countGap;
    Assertions.assertFalse(FeatureItemBackpressure.shouldThrottle(tickTimeMs, triggerTickTimeMs, entityCount, triggerEntityCount));
  }

  @Property(tries = 200)
  void raisingTickTimeNeverClearsThrottle(@ForAll @DoubleRange(min = 0.0, max = 10_000.0) double tickTimeMs,
                                          @ForAll @DoubleRange(min = 0.0, max = 5_000.0) double bump,
                                          @ForAll @DoubleRange(min = 0.0, max = 10_000.0) double triggerTickTimeMs,
                                          @ForAll @DoubleRange(min = 0.0, max = 1_000_000.0) double entityCount,
                                          @ForAll @DoubleRange(min = 0.0, max = 1_000_000.0) double triggerEntityCount) {
    boolean before = FeatureItemBackpressure.shouldThrottle(tickTimeMs, triggerTickTimeMs, entityCount, triggerEntityCount);
    boolean after = FeatureItemBackpressure.shouldThrottle(tickTimeMs + bump, triggerTickTimeMs, entityCount, triggerEntityCount);
    if (before) {
      Assertions.assertTrue(after);
    }
  }

  @Property(tries = 200)
  void raisingEntityCountNeverClearsThrottle(@ForAll @DoubleRange(min = 0.0, max = 10_000.0) double tickTimeMs,
                                             @ForAll @DoubleRange(min = 0.0, max = 1_000_000.0) double entityCount,
                                             @ForAll @DoubleRange(min = 0.0, max = 500_000.0) double bump,
                                             @ForAll @DoubleRange(min = 0.0, max = 10_000.0) double triggerTickTimeMs,
                                             @ForAll @DoubleRange(min = 0.0, max = 1_000_000.0) double triggerEntityCount) {
    boolean before = FeatureItemBackpressure.shouldThrottle(tickTimeMs, triggerTickTimeMs, entityCount, triggerEntityCount);
    boolean after = FeatureItemBackpressure.shouldThrottle(tickTimeMs, triggerTickTimeMs, entityCount + bump, triggerEntityCount);
    if (before) {
      Assertions.assertTrue(after);
    }
  }
}
