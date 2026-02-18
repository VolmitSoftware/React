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
      return 0D;
    }

    return controller.getRemoteSamplerBridge().valueOr(pluginId, metricKey(), 0D);
  }

  @Override
  public String formattedValue(double t) {
    if (!isAvailable()) {
      return "---";
    }
    return Form.f(t, decimals);
  }

  @Override
  public String formattedSuffix(double t) {
    if (!isAvailable()) {
      return "";
    }
    return suffix;
  }

  protected boolean isAvailable() {
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
