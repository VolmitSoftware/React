package art.arcane.react.core.integration;

public final class IrisPregenPressure {
  public static final int DEFAULT_IN_FLIGHT_THRESHOLD = 4;
  public static final int MAX_IN_FLIGHT_THRESHOLD = 256;
  public static final double DEFAULT_TICK_GATE_MS = 50D;
  public static final double MAX_TICK_GATE_MS = 1000D;

  private IrisPregenPressure() {
  }

  public static int clampInFlightThreshold(int configuredThreshold) {
    return Math.max(1, Math.min(MAX_IN_FLIGHT_THRESHOLD, configuredThreshold));
  }

  public static double clampTickGateMS(double configuredGateMS) {
    if (!Double.isFinite(configuredGateMS)) {
      return DEFAULT_TICK_GATE_MS;
    }
    return Math.max(1D, Math.min(MAX_TICK_GATE_MS, configuredGateMS));
  }

  public static boolean hasInFlightPressure(double pregenInFlight, int configuredThreshold) {
    return Double.isFinite(pregenInFlight) && pregenInFlight >= clampInFlightThreshold(configuredThreshold);
  }

  public static boolean hasInFlightPressureUnderLoad(double pregenInFlight,
                                                     int configuredThreshold,
                                                     double tickMS,
                                                     double configuredGateMS) {
    return hasInFlightPressure(pregenInFlight, configuredThreshold)
        && Double.isFinite(tickMS)
        && tickMS >= clampTickGateMS(configuredGateMS);
  }
}
