package art.arcane.react.content.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisTerrainSurgeTriggerTest {
  private static final FeatureIrisTerrainSurgeGuard.SurgeTriggers DEFAULTS =
      new FeatureIrisTerrainSurgeGuard.SurgeTriggers(56D, 24D, 4, 50D);

  @Test
  void healthyServerNeverSurgesOnARunningPregen() {
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(20D, -1D, 256D, DEFAULTS));
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(49.9D, -1D, 256D, DEFAULTS));
  }

  @Test
  void worldPregenAtTheInFlightThresholdSurgesOnceTickTimeReachesThePregenGate() {
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(50D, -1D, 3D, DEFAULTS));
    assertTrue(FeatureIrisTerrainSurgeGuard.shouldSurge(50D, -1D, 4D, DEFAULTS));
    assertTrue(FeatureIrisTerrainSurgeGuard.shouldSurge(53D, -1D, 128D, DEFAULTS));
  }

  @Test
  void idleOrUnavailableWorldPregenNeverLowersTheTickTrigger() {
    FeatureIrisTerrainSurgeGuard.SurgeTriggers zeroThreshold =
        new FeatureIrisTerrainSurgeGuard.SurgeTriggers(56D, 24D, 0, 50D);
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(52D, -1D, 0D, zeroThreshold));
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(52D, -1D, -1D, DEFAULTS));
  }

  @Test
  void pregenTickGateClampsSoItCanNeverBecomeAnUngatedLatch() {
    FeatureIrisTerrainSurgeGuard.SurgeTriggers zeroGate =
        new FeatureIrisTerrainSurgeGuard.SurgeTriggers(56D, 24D, 4, 0D);
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(0.5D, -1D, 64D, zeroGate));
    assertTrue(FeatureIrisTerrainSurgeGuard.shouldSurge(1D, -1D, 64D, zeroGate));
  }

  @Test
  void oversizedPregenThresholdClampsToTheLargestIrisConcurrencyCap() {
    FeatureIrisTerrainSurgeGuard.SurgeTriggers oversized =
        new FeatureIrisTerrainSurgeGuard.SurgeTriggers(56D, 24D, 5_000, 50D);
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(52D, -1D, 255D, oversized));
    assertTrue(FeatureIrisTerrainSurgeGuard.shouldSurge(52D, -1D, 256D, oversized));
  }

  @Test
  void tickAndGenerationTriggersStillApply() {
    assertTrue(FeatureIrisTerrainSurgeGuard.shouldSurge(56D, -1D, -1D, DEFAULTS));
    assertTrue(FeatureIrisTerrainSurgeGuard.shouldSurge(20D, 24D, -1D, DEFAULTS));
    assertFalse(FeatureIrisTerrainSurgeGuard.shouldSurge(55.9D, 23.9D, -1D, DEFAULTS));
  }
}
