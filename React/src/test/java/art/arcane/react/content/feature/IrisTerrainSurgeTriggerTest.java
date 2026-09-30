package art.arcane.react.content.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisTerrainSurgeTriggerTest {
  @Test
  void worldPregenAtTheInFlightThresholdSurgesOnItsOwn() {
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(20D, -1D, 15D, 56D, 24D, 16));
    assertTrue(FeatureIrisTerrainSurgeGuard.shouldSurge(20D, -1D, 16D, 56D, 24D, 16));
  }

  @Test
  void idleOrUnavailableWorldPregenNeverSurges() {
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(20D, -1D, 0D, 56D, 24D, 0));
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(20D, -1D, -1D, 56D, 24D, 16));
  }

  @Test
  void oversizedPregenThresholdClampsToTheLargestIrisConcurrencyCap() {
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(20D, -1D, 255D, 56D, 24D, 5_000));
    assertTrue(FeatureIrisTerrainSurgeGuard.shouldSurge(20D, -1D, 256D, 56D, 24D, 5_000));
  }

  @Test
  void tickAndGenerationTriggersStillApply() {
    assertTrue(FeatureIrisTerrainSurgeGuard.shouldSurge(56D, -1D, -1D, 56D, 24D, 16));
    assertTrue(FeatureIrisTerrainSurgeGuard.shouldSurge(20D, 24D, -1D, 56D, 24D, 16));
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(55.9D, 23.9D, -1D, 56D, 24D, 16));
  }
}
