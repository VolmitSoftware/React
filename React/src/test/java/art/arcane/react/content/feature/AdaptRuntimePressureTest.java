package art.arcane.react.content.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptRuntimePressureTest {
  @Test
  void overlayAddsPressureOnlyFromMeasuredTimingAndSessionLoad() {
    double baseOnly = FeatureAdaptRuntimePressureOverlay.pressureScore(100D, 0D, 0D);
    assertEquals(35D, baseOnly, 0.0001D);
    assertTrue(FeatureAdaptRuntimePressureOverlay.pressureScore(100D, 0D, 100D) > baseOnly);
    assertTrue(FeatureAdaptRuntimePressureOverlay.pressureScore(100D, 70D, 0D) > baseOnly);
  }

  @Test
  void surgeGuardUsesMeasuredTimingBudgetInsteadOfOperationVolume() {
    assertFalse(FeatureAdaptRuntimeSurgeGuard.shouldSurge(50D, 20D, 99.9D, 58D, 70D, 100D));
    assertTrue(FeatureAdaptRuntimeSurgeGuard.shouldSurge(50D, 20D, 100D, 58D, 70D, 100D));
    assertTrue(FeatureAdaptRuntimeSurgeGuard.shouldSurge(58D, 20D, 0D, 58D, 70D, 100D));
    assertTrue(FeatureAdaptRuntimeSurgeGuard.shouldSurge(50D, 70D, 0D, 58D, 70D, 100D));
  }

  @Test
  void trinityRequiresServerPressureAfterAdaptTimingPressure() {
    boolean adaptPressure = FeatureTrinityIncidentMode.hasAdaptPressure(20D, 100D, 72D, 100D);
    assertTrue(adaptPressure);
    assertFalse(FeatureTrinityIncidentMode.hasServerPressure(61.9D, 61.9D, 62D, 62D));
    assertTrue(FeatureTrinityIncidentMode.hasServerPressure(62D, 20D, 62D, 62D));
    assertTrue(FeatureTrinityIncidentMode.hasServerPressure(20D, 62D, 62D, 62D));
  }
}
