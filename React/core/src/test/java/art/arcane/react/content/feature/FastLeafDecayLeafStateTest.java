package art.arcane.react.content.feature;

import art.arcane.react.platform.GrowthHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

class FastLeafDecayLeafStateTest {
  @Test
  void decaysOnlyNaturalLeavesAtTheConfiguredDistance() {
    FeatureFastLeafDecay feature = new FeatureFastLeafDecay();

    Assertions.assertFalse(feature.shouldDecay(GrowthHost.NOT_LEAVES));
    Assertions.assertFalse(feature.shouldDecay(5));
    Assertions.assertTrue(feature.shouldDecay(6));
    Assertions.assertTrue(feature.shouldDecay(7));
    Assertions.assertFalse(feature.shouldDecay(7 | GrowthHost.PERSISTENT_LEAVES));
  }

  @Test
  void forceDecayPersistentAlsoDecaysPlacedLeaves() throws ReflectiveOperationException {
    FeatureFastLeafDecay feature = new FeatureFastLeafDecay();
    Field field = FeatureFastLeafDecay.class.getDeclaredField("forceDecayPersistent");
    field.setAccessible(true);
    field.setBoolean(feature, true);

    Assertions.assertTrue(feature.shouldDecay(7 | GrowthHost.PERSISTENT_LEAVES));
    Assertions.assertFalse(feature.shouldDecay(3 | GrowthHost.PERSISTENT_LEAVES));
  }

  @Test
  void anyLeafStateCountsAsLeaves() {
    FeatureFastLeafDecay feature = new FeatureFastLeafDecay();

    Assertions.assertTrue(feature.isLeaf(1));
    Assertions.assertTrue(feature.isLeaf(1 | GrowthHost.PERSISTENT_LEAVES));
    Assertions.assertFalse(feature.isLeaf(GrowthHost.NOT_LEAVES));
  }
}
