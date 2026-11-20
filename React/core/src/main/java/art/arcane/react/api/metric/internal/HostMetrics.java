package art.arcane.react.api.metric.internal;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.core.controller.SampleController;

import java.util.Set;

/** React's own samplers, as {@code ReactMetrics.hostMetricKeys()} and {@code readHostMetric} show them. */
public final class HostMetrics {
  private HostMetrics() {
  }

  public static void install() {
    MetricInstaller.installHostMetrics(HostMetrics::keys, HostMetrics::read);
  }

  public static void uninstall() {
    MetricInstaller.installHostMetrics(null, null);
  }

  static Set<String> keys() {
    SampleController controller = SamplerHosts.sampleController();
    if (controller == null || controller.getSamplers() == null) {
      return Set.of();
    }
    return Set.copyOf(controller.getSamplers().ids());
  }

  static double read(String key) {
    SampleController controller = SamplerHosts.sampleController();
    Sampler sampler = controller == null ? null : controller.getSampler(key);
    if (sampler == null) {
      return Double.NaN;
    }
    double value = sampler.sample();
    return sampler.isSampleAvailable() ? value : Double.NaN;
  }
}
