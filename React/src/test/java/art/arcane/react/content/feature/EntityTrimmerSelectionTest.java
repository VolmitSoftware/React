package art.arcane.react.content.feature;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EntityTrimmerSelectionTest {
  @Test
  void overflowHonorsEveryCapIncludingDisabledAndZeroCaps() {
    Assertions.assertEquals(0, FeatureEntityTrimmer.overflow(100, -1));
    Assertions.assertEquals(0, FeatureEntityTrimmer.overflow(11, 11));
    Assertions.assertEquals(1, FeatureEntityTrimmer.overflow(12, 11));
    Assertions.assertEquals(12, FeatureEntityTrimmer.overflow(12, 0));
  }

  @Test
  void minimumBatchIsAppliedAfterTheOpportunityFraction() {
    Assertions.assertEquals(0, FeatureEntityTrimmer.plannedRemovals(399, 0.25D, 100));
    Assertions.assertEquals(100, FeatureEntityTrimmer.plannedRemovals(400, 0.25D, 100));
    Assertions.assertEquals(125, FeatureEntityTrimmer.plannedRemovals(500, 0.25D, 100));
  }

  @Test
  void opportunityFractionIsBoundedAndRejectsNonFiniteValues() {
    Assertions.assertEquals(0, FeatureEntityTrimmer.plannedRemovals(100, -1D, 1));
    Assertions.assertEquals(100, FeatureEntityTrimmer.plannedRemovals(100, 2D, 1));
    Assertions.assertEquals(512, FeatureEntityTrimmer.plannedRemovals(10000, 1D, 1));
    Assertions.assertEquals(0, FeatureEntityTrimmer.plannedRemovals(100, Double.NaN, 1));
  }
}
