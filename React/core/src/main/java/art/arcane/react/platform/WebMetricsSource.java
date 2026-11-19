package art.arcane.react.platform;

import art.arcane.react.api.web.dto.MetricDiagnosticsDto;

import java.util.List;
import java.util.Optional;

/** The sampler values and metric history React Web reads, whatever keeps them on this platform. */
public interface WebMetricsSource {
  /**
   * The latest captured values. Returns the same instance until the next capture; when nothing was captured yet, a live
   * read of every sampler with sequence 0, built fresh on each call.
   */
  Frame currentFrame();

  /** Empty on a platform that keeps no metric history; the history routes then answer 503. */
  default Optional<WebMetricHistory> history() {
    return Optional.empty();
  }

  record Frame(long sequence, long capturedAtMs, List<Value> values) {
    public Frame {
      values = List.copyOf(values);
    }
  }

  /**
   * One sampler's reading. {@code quality} is UNKNOWN, MEASURED or ESTIMATED; {@code diagnostics} is null when the sampler
   * reports none.
   */
  record Value(String id, String name, String suffix, double value, String display, boolean available, long sampledAtMs,
               long intervalMs, long windowMs, String quality, String unit, String unavailableReason,
               MetricDiagnosticsDto diagnostics) {
  }

  static WebMetricsSource empty() {
    return () -> new Frame(0L, System.currentTimeMillis(), List.of());
  }
}
