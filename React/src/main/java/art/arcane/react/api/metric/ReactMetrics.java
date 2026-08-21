package art.arcane.react.api.metric;

import art.arcane.react.api.metric.internal.MetricBinding;
import art.arcane.react.api.metric.internal.MetricInstaller;

import java.util.Set;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

public final class ReactMetrics {
  private ReactMetrics() {
  }

  public static boolean available() {
    return MetricInstaller.binding() != null;
  }

  public static boolean accepting(String sourceId) {
    MetricBinding current = MetricInstaller.binding();
    return current != null && current.accepting(sourceId);
  }

  public static boolean publish(String sourceId, String key, double value) {
    return publish(sourceId, key, value, System.currentTimeMillis());
  }

  public static boolean publish(String sourceId, String key, double value, long sampledAtMillis) {
    MetricBinding current = MetricInstaller.binding();
    return current != null && current.publish(sourceId, key, value, sampledAtMillis, System.currentTimeMillis());
  }

  public static void withdraw(String sourceId, String key) {
    MetricBinding current = MetricInstaller.binding();

    if (current != null) {
      current.withdraw(sourceId, key);
    }
  }

  public static Set<String> publishedSourceIds() {
    MetricBinding current = MetricInstaller.binding();
    return current == null ? Set.of() : current.sourceIds();
  }

  public static Set<String> hostMetricKeys() {
    Supplier<Set<String>> supplier = MetricInstaller.hostKeys();

    if (supplier == null) {
      return Set.of();
    }

    Set<String> keys = supplier.get();
    return keys == null ? Set.of() : keys;
  }

  public static double readHostMetric(String key) {
    ToDoubleFunction<String> reader = MetricInstaller.hostReader();

    if (reader == null || key == null || key.isBlank()) {
      return Double.NaN;
    }

    return reader.applyAsDouble(key);
  }

  public static boolean hostMetricAvailable(String key) {
    return Double.isFinite(readHostMetric(key));
  }
}
