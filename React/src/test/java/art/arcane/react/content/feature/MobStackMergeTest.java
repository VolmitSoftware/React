package art.arcane.react.content.feature;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MobStackMergeTest {

  @Test
  void stackLimitNotExceededWhenSumUnderMax() {
    Assertions.assertFalse(FeatureMobStacking.exceedsStackLimit(5, 3, 10));
  }

  @Test
  void stackLimitExceededWhenSumOverMax() {
    Assertions.assertTrue(FeatureMobStacking.exceedsStackLimit(7, 4, 10));
  }

  @Test
  void stackLimitNotExceededWhenSumEqualsMax() {
    Assertions.assertFalse(FeatureMobStacking.exceedsStackLimit(5, 5, 10));
  }

  @Test
  void healthLimitWithinWhenCombinedUnderMax() {
    Assertions.assertTrue(FeatureMobStacking.withinHealthLimit(20.0, 20.0, 100.0));
  }

  @Test
  void healthLimitExceededWhenCombinedOverMax() {
    Assertions.assertFalse(FeatureMobStacking.withinHealthLimit(60.0, 50.0, 100.0));
  }

  @Test
  void healthLimitWithinWhenCombinedEqualsMax() {
    Assertions.assertTrue(FeatureMobStacking.withinHealthLimit(50.0, 50.0, 100.0));
  }

  @Test
  void theoreticalMaxStackCountFromHealthRatio() {
    Assertions.assertEquals(5, FeatureMobStacking.theoreticalMaxStackCount(100.0, 20.0, 10));
  }

  @Test
  void theoreticalMaxStackCountClampedByConfiguredMax() {
    Assertions.assertEquals(3, FeatureMobStacking.theoreticalMaxStackCount(100.0, 20.0, 3));
  }

  @Test
  void theoreticalMaxStackCountClampedByMaxWhenRatioHigher() {
    Assertions.assertEquals(10, FeatureMobStacking.theoreticalMaxStackCount(100.0, 7.0, 10));
  }

  @Test
  void theoreticalMaxStackCountZeroWhenEntityHealthExceedsBudget() {
    Assertions.assertEquals(0, FeatureMobStacking.theoreticalMaxStackCount(15.0, 20.0, 10));
  }

  @Property(tries = 200)
  void exceedsStackLimitMonotonicInSourceCount(@ForAll @IntRange(min = 0, max = 64) int intoCount,
                                               @ForAll @IntRange(min = 0, max = 64) int sourceCount,
                                               @ForAll @IntRange(min = 0, max = 64) int extra,
                                               @ForAll @IntRange(min = 0, max = 128) int maxStackSize) {
    boolean low = FeatureMobStacking.exceedsStackLimit(intoCount, sourceCount, maxStackSize);
    boolean high = FeatureMobStacking.exceedsStackLimit(intoCount, sourceCount + extra, maxStackSize);
    if (low) {
      Assertions.assertTrue(high);
    }
  }

  @Property(tries = 200)
  void withinHealthLimitMonotonicInCombinedHealth(@ForAll @DoubleRange(min = 0.0, max = 200.0) double sourceHealth,
                                                  @ForAll @DoubleRange(min = 0.0, max = 200.0) double intoHealth,
                                                  @ForAll @DoubleRange(min = 0.0, max = 200.0) double extra,
                                                  @ForAll @DoubleRange(min = 0.0, max = 400.0) double maxHealth) {
    boolean low = FeatureMobStacking.withinHealthLimit(sourceHealth, intoHealth, maxHealth);
    boolean high = FeatureMobStacking.withinHealthLimit(sourceHealth + extra, intoHealth, maxHealth);
    if (!low) {
      Assertions.assertFalse(high);
    }
  }

  @Property(tries = 200)
  void theoreticalMaxStackCountStaysWithinBounds(@ForAll @DoubleRange(min = 1.0, max = 2000.0) double maxHealth,
                                                 @ForAll @DoubleRange(min = 0.5, max = 200.0) double entityMaxHealth,
                                                 @ForAll @IntRange(min = 1, max = 64) int maxStackSize) {
    int result = FeatureMobStacking.theoreticalMaxStackCount(maxHealth, entityMaxHealth, maxStackSize);
    Assertions.assertTrue(result >= 0 && result <= maxStackSize);
  }

  @Property(tries = 200)
  void theoreticalMaxStackCountMonotonicInHealthBudget(@ForAll @DoubleRange(min = 1.0, max = 2000.0) double maxHealth,
                                                       @ForAll @DoubleRange(min = 0.0, max = 2000.0) double bump,
                                                       @ForAll @DoubleRange(min = 0.5, max = 200.0) double entityMaxHealth,
                                                       @ForAll @IntRange(min = 1, max = 64) int maxStackSize) {
    int low = FeatureMobStacking.theoreticalMaxStackCount(maxHealth, entityMaxHealth, maxStackSize);
    int high = FeatureMobStacking.theoreticalMaxStackCount(maxHealth + bump, entityMaxHealth, maxStackSize);
    Assertions.assertTrue(high >= low);
  }
}
