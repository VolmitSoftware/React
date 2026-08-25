package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.volmlib.util.format.Form;

abstract class RemoteIntegrationSampler extends ReactCachedSampler {
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
  public double onSample() {
    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null || controller.getRemoteSamplerBridge() == null) {
      return hasAvailableValue ? lastAvailableValue : 0D;
    }

    if (!controller.getRemoteSamplerBridge().isAvailable(pluginId, metricKey())) {
      return hasAvailableValue ? lastAvailableValue : 0D;
    }
    double value = controller.getRemoteSamplerBridge().valueOr(pluginId, metricKey(), 0D);
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
    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null || controller.getRemoteSamplerBridge() == null) {
      return false;
    }

    return controller.getRemoteSamplerBridge().isAvailable(pluginId, metricKey());
  }

  protected String metricKey() {
    return defaultMetricKey;
  }
}
