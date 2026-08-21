package art.arcane.react.api.metric.internal;

import java.util.Set;

public interface MetricBinding {
  boolean accepting(String sourceId);

  boolean publish(String sourceId, String key, double value, long sampledAtMs, long nowMs);

  void withdraw(String sourceId, String key);

  Set<String> sourceIds();
}
