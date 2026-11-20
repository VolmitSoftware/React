package art.arcane.react.platform;

import java.util.Map;

/**
 * Event bus timing published by the platform's event profiler.
 */
public interface EventMeasurement {
  boolean isMeasuring();

  long getWindowPublishedAtMs();

  double getWindowSeconds();

  void markSamplerActivity();

  double getEventTimeMsPerSecond();

  int getListenerCount();

  double getCallsPerTick();

  /**
   * Event time in milliseconds per plugin or mod over the published window, keyed by owner id.
   */
  Map<String, Double> snapshotPluginEventTimeMS();

  Map<String, Integer> snapshotPluginEventCalls();

  /**
   * Display names by owner id, for owners whose id is not what players should read.
   */
  default Map<String, String> ownerDisplayNames() {
    return Map.of();
  }
}
