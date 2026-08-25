package art.arcane.react.core.integration;

import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.ReactMetricKind;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.Objects;

public class PublishedMetricSampler extends ReactCachedSampler {
  private transient volatile ReactMetric metric;
  private final transient PublishedMetricStore store;
  private final transient String samplerId;
  private transient double lastAvailableValue;
  private transient boolean hasAvailableValue;

  public PublishedMetricSampler(String samplerId, ReactMetric metric, PublishedMetricStore store) {
    super(samplerId, 1000);
    this.samplerId = samplerId;
    this.metric = metric;
    this.store = store;
  }

  public ReactMetric metric() {
    return metric;
  }

  public void updateMetric(ReactMetric metric) {
    ReactMetric replacement = Objects.requireNonNull(metric, "metric");
    if (!this.metric.key().equals(replacement.key())) {
      throw new IllegalArgumentException("Metric key cannot change for an existing sampler");
    }

    this.metric = replacement;
  }

  @Override
  public String getId() {
    return samplerId;
  }

  @Override
  public String getName() {
    return metric().displayName();
  }

  @Override
  public Material getIcon() {
    return metric().icon();
  }

  @Override
  public void loadConfiguration() {
  }

  @Override
  public boolean reloadConfiguration() {
    return true;
  }

  @Override
  public void start() {
    super.start();
    lastAvailableValue = 0D;
    hasAvailableValue = false;
  }

  @Override
  public double onSample() {
    long now = System.currentTimeMillis();
    ReactMetric currentMetric = metric();

    if (!store.available(currentMetric.key(), now)) {
      return hasAvailableValue ? lastAvailableValue : 0D;
    }

    double value = store.valueOr(currentMetric.key(), 0D, now);

    if (Double.isFinite(value)) {
      lastAvailableValue = value;
      hasAvailableValue = true;
    }

    return hasAvailableValue ? lastAvailableValue : 0D;
  }

  @Override
  public String formattedValue(double t) {
    if (!isSampleAvailable()) {
      return "---";
    }

    return Form.f(t, metric().decimals());
  }

  @Override
  public String formattedSuffix(double t) {
    if (!isSampleAvailable()) {
      return "";
    }

    return suffixFor(metric());
  }

  static String suffixFor(ReactMetric metric) {
    if (!metric.unit().isEmpty()) {
      return metric.unit();
    }

    return switch (metric.kind()) {
      case PERCENT -> "%";
      case MILLIS -> "ms";
      case BYTES -> "B";
      case RATE -> "/s";
      default -> "";
    };
  }

  static int defaultDecimalsFor(ReactMetricKind kind) {
    return switch (kind) {
      case GAUGE, COUNTER, BYTES -> 0;
      case RATE, PERCENT -> 1;
      case MILLIS -> 2;
      default -> 0;
    };
  }

  @Override
  public boolean isSampleAvailable() {
    return store.available(metric().key(), System.currentTimeMillis());
  }
}
