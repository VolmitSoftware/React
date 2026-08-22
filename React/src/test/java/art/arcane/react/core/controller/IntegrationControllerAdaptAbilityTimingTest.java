package art.arcane.react.core.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationControllerAdaptAbilityTimingTest {
  @Test
  void measuredTimingAndMsptMustBothShowPressure() {
    assertFalse(IntegrationController.shouldReportAdaptAbilityTiming(99.9D, 3, 60D));
    assertFalse(IntegrationController.shouldReportAdaptAbilityTiming(100D, 3, 49.9D));
    assertTrue(IntegrationController.shouldReportAdaptAbilityTiming(100D, 3, 50D));
  }

  @Test
  void pressureMustRemainSustainedForThreeSamples() {
    int streak = IntegrationController.nextAdaptAbilityTimingImpactStreak(0, 100D, 50D);
    assertEquals(1, streak);
    streak = IntegrationController.nextAdaptAbilityTimingImpactStreak(streak, 120D, 55D);
    assertEquals(2, streak);
    assertFalse(IntegrationController.shouldReportAdaptAbilityTiming(120D, streak, 55D));
    streak = IntegrationController.nextAdaptAbilityTimingImpactStreak(streak, 110D, 52D);
    assertEquals(3, streak);
    assertTrue(IntegrationController.shouldReportAdaptAbilityTiming(110D, streak, 52D));
  }

  @Test
  void eitherRecoveredSignalResetsTheStreak() {
    assertEquals(0, IntegrationController.nextAdaptAbilityTimingImpactStreak(8, 99.9D, 60D));
    assertEquals(0, IntegrationController.nextAdaptAbilityTimingImpactStreak(8, 120D, 49.9D));
  }

  @Test
  void unavailableTimingCannotProduceAReport() {
    assertFalse(IntegrationController.shouldReportAdaptAbilityTiming(-1D, 10, 60D));
    assertFalse(IntegrationController.shouldReportAdaptAbilityTiming(Double.NaN, 10, 60D));
    assertFalse(IntegrationController.shouldReportAdaptAbilityTiming(Double.POSITIVE_INFINITY, 10, 60D));
  }
}
