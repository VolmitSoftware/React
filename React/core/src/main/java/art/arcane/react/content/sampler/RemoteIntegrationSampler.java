package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.integration.IntegrationMetricSample;

/**
 * One metric another plugin or mod publishes to React, read from the platform's integration host.
 */
public abstract class RemoteIntegrationSampler extends ReactCachedSampler {
  private final String pluginId;
  private final String defaultMetricKey;
  private final int decimals;
  private final String suffix;
  private transient double lastAvailableValue;
  private transient boolean hasAvailableValue;

  protected RemoteIntegrationSampler(
      String id,
      String pluginId,
      String metricKey,
      int decimals,
      String suffix
  ) {
    super(id, 1000);
    this.pluginId = pluginId;
    this.defaultMetricKey = metricKey;
    this.decimals = Math.max(0, decimals);
    this.suffix = suffix == null ? "" : suffix;
  }

  @Override
  public Reading captureReading() {
    IntegrationMetricSample sample = storedSample();
    return sample == null ? reading(0D, 0L, false)
        : reading(sample.valueOr(0D), sample.sampledAtMs(), sample.available());
  }

  @Override
  public Metadata metadata() {
    return new Metadata(sampleDelay, 0L, Quality.UNKNOWN, suffix.trim());
  }

  @Override
  public double onSample() {
    IntegrationMetricSample sample = storedSample();
    if (sample == null || !sample.available()) {
      return hasAvailableValue ? lastAvailableValue : 0D;
    }
    double value = sample.valueOr(0D);
    if (Double.isFinite(value)) {
      lastAvailableValue = value;
      hasAvailableValue = true;
    }
    return hasAvailableValue ? lastAvailableValue : 0D;
  }

  @Override
  public void start() {
    super.start();
    lastAvailableValue = 0D;
    hasAvailableValue = false;
  }

  @Override
  public String formattedValue(double t) {
    if (!isSampleAvailable()) {
      return "---";
    }
    return Form.f(t, decimals);
  }

  @Override
  public String formattedSuffix(double t) {
    if (!isSampleAvailable()) {
      return "";
    }
    return suffix;
  }

  @Override
  public boolean isSampleAvailable() {
    IntegrationMetricSample sample = storedSample();
    return sample != null && sample.available();
  }

  private IntegrationMetricSample storedSample() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    IntegrationMetricsHost host = platform == null ? null : platform.integrationMetricsHost().orElse(null);
    return host == null ? null : host.storedSample(pluginId, metricKey());
  }

  protected String metricKey() {
    return defaultMetricKey;
  }
}
