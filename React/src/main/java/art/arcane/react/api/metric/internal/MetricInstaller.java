package art.arcane.react.api.metric.internal;

import java.util.Set;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

public final class MetricInstaller {
  private static volatile MetricBinding installed;
  private static volatile Supplier<Set<String>> hostKeys;
  private static volatile ToDoubleFunction<String> hostReader;

  private MetricInstaller() {
  }

  public static void install(MetricBinding binding) {
    installed = binding;
  }

  public static void uninstall(MetricBinding binding) {
    if (installed == binding) {
      installed = null;
    }
  }

  public static void installHostMetrics(Supplier<Set<String>> keys, ToDoubleFunction<String> reader) {
    hostKeys = keys;
    hostReader = reader;
  }

  public static MetricBinding binding() {
    return installed;
  }

  public static Supplier<Set<String>> hostKeys() {
    return hostKeys;
  }

  public static ToDoubleFunction<String> hostReader() {
    return hostReader;
  }
}
