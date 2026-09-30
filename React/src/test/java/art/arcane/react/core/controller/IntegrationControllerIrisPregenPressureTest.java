package art.arcane.react.core.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationControllerIrisPregenPressureTest {
  @Test
  void runningPregenAloneIsNotReported() {
    int streak = 0;
    for (int sample = 0; sample < 10; sample++) {
      streak = IntegrationController.nextIrisPregenImpactStreak(streak, 256D, 20D);
    }

    assertEquals(0, streak);
    assertFalse(IntegrationController.shouldReportIrisPregenPressure(256D, streak, 20D));
  }

  @Test
  void pregenAtTheInFlightThresholdWithElevatedMsptIsReportedAfterThreeSamples() {
    int streak = IntegrationController.nextIrisPregenImpactStreak(0, 16D, 50D);
    assertEquals(1, streak);
    streak = IntegrationController.nextIrisPregenImpactStreak(streak, 32D, 55D);
    assertFalse(IntegrationController.shouldReportIrisPregenPressure(32D, streak, 55D));
    streak = IntegrationController.nextIrisPregenImpactStreak(streak, 24D, 51D);

    assertEquals(3, streak);
    assertTrue(IntegrationController.shouldReportIrisPregenPressure(24D, streak, 51D));
  }

  @Test
  void pregenBelowTheThresholdOrRecoveredMsptResetsTheStreak() {
    assertEquals(0, IntegrationController.nextIrisPregenImpactStreak(8, 15D, 60D));
    assertEquals(0, IntegrationController.nextIrisPregenImpactStreak(8, 64D, 49.9D));
    assertFalse(IntegrationController.shouldReportIrisPregenPressure(15D, 8, 60D));
  }

  @Test
  void unavailablePregenCannotProduceAReport() {
    assertFalse(IntegrationController.shouldReportIrisPregenPressure(-1D, 10, 60D));
    assertFalse(IntegrationController.shouldReportIrisPregenPressure(Double.NaN, 10, 60D));
    assertEquals(0, IntegrationController.nextIrisPregenImpactStreak(4, -1D, 60D));
  }
}
