package art.arcane.react.core.integration;

public final class IrisPregenPressure {
  public static final int DEFAULT_IN_FLIGHT_THRESHOLD = 16;
  public static final int MAX_IN_FLIGHT_THRESHOLD = 256;

  private IrisPregenPressure() {
  }

  public static int clampInFlightThreshold(int configuredThreshold) {
    return Math.max(1, Math.min(MAX_IN_FLIGHT_THRESHOLD, configuredThreshold));
  }

  public static boolean hasInFlightPressure(double pregenInFlight, int configuredThreshold) {
    return Double.isFinite(pregenInFlight) && pregenInFlight >= clampInFlightThreshold(configuredThreshold);
  }
}
