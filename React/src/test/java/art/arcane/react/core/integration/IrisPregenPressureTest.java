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
  void defaultThresholdIsTheIrisAdaptiveInFlightFloor() {
    assertEquals(4, IrisPregenPressure.DEFAULT_IN_FLIGHT_THRESHOLD);
    assertTrue(IrisPregenPressure.hasInFlightPressure(4D, IrisPregenPressure.DEFAULT_IN_FLIGHT_THRESHOLD));
    assertTrue(IrisPregenPressure.hasInFlightPressure(8D, IrisPregenPressure.DEFAULT_IN_FLIGHT_THRESHOLD));
    assertFalse(IrisPregenPressure.hasInFlightPressure(3D, IrisPregenPressure.DEFAULT_IN_FLIGHT_THRESHOLD));
  }

  @Test
  void tickGateClampsToOneThroughOneThousandMilliseconds() {
    assertEquals(1D, IrisPregenPressure.clampTickGateMS(0D));
    assertEquals(1D, IrisPregenPressure.clampTickGateMS(-20D));
    assertEquals(50D, IrisPregenPressure.clampTickGateMS(50D));
    assertEquals(1000D, IrisPregenPressure.clampTickGateMS(5_000D));
    assertEquals(IrisPregenPressure.DEFAULT_TICK_GATE_MS, IrisPregenPressure.clampTickGateMS(Double.NaN));
  }

  @Test
  void pressureUnderLoadRequiresBothInFlightRequestsAndTickTime() {
    assertFalse(IrisPregenPressure.hasInFlightPressureUnderLoad(256D, 4, 49.9D, 50D));
    assertFalse(IrisPregenPressure.hasInFlightPressureUnderLoad(3D, 4, 80D, 50D));
    assertTrue(IrisPregenPressure.hasInFlightPressureUnderLoad(4D, 4, 50D, 50D));
    assertFalse(IrisPregenPressure.hasInFlightPressureUnderLoad(64D, 4, Double.NaN, 50D));
    assertFalse(IrisPregenPressure.hasInFlightPressureUnderLoad(-1D, 4, 80D, 50D));
  }
}
