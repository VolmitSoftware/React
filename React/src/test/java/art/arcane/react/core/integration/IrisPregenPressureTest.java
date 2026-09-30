package art.arcane.react.core.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisPregenPressureTest {
  @Test
  void thresholdClampsToTheIrisPregenConcurrencyRange() {
    assertEquals(1, IrisPregenPressure.clampInFlightThreshold(0));
    assertEquals(1, IrisPregenPressure.clampInFlightThreshold(-3));
    assertEquals(16, IrisPregenPressure.clampInFlightThreshold(16));
    assertEquals(256, IrisPregenPressure.clampInFlightThreshold(256));
    assertEquals(256, IrisPregenPressure.clampInFlightThreshold(5_000));
  }

  @Test
  void pressureStartsAtTheThreshold() {
    assertFalse(IrisPregenPressure.hasInFlightPressure(15D, 16));
    assertTrue(IrisPregenPressure.hasInFlightPressure(16D, 16));
    assertTrue(IrisPregenPressure.hasInFlightPressure(40D, 16));
  }

  @Test
  void idleOrUnavailablePregenNeverCountsAsPressure() {
    assertFalse(IrisPregenPressure.hasInFlightPressure(0D, 0));
    assertFalse(IrisPregenPressure.hasInFlightPressure(-1D, 1));
    assertFalse(IrisPregenPressure.hasInFlightPressure(-1D, 16));
    assertFalse(IrisPregenPressure.hasInFlightPressure(Double.NaN, 16));
    assertFalse(IrisPregenPressure.hasInFlightPressure(Double.POSITIVE_INFINITY, 16));
  }

  @Test
  void oversizedThresholdStillFiresAtTheLargestIrisConcurrencyCap() {
    assertFalse(IrisPregenPressure.hasInFlightPressure(255D, 5_000));
    assertTrue(IrisPregenPressure.hasInFlightPressure(256D, 5_000));
  }

  @Test
  void defaultThresholdIsTheSmallestIrisPaperConcurrencyCap() {
    assertEquals(16, IrisPregenPressure.DEFAULT_IN_FLIGHT_THRESHOLD);
    assertTrue(IrisPregenPressure.hasInFlightPressure(16D, IrisPregenPressure.DEFAULT_IN_FLIGHT_THRESHOLD));
    assertFalse(IrisPregenPressure.hasInFlightPressure(15D, IrisPregenPressure.DEFAULT_IN_FLIGHT_THRESHOLD));
  }
}
