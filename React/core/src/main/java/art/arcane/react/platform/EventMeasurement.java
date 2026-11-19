package art.arcane.react.platform;

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
}
