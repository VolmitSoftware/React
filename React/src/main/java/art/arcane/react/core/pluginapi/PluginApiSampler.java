package art.arcane.react.core.pluginapi;

import art.arcane.react.api.metric.ReactMetricKind;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

public final class PluginApiSampler extends ReactCachedSampler {
  private final String packId;
  private final String targetPlugin;
  private final PluginApiMetricRuntime runtime;

  public PluginApiSampler(String packId, String targetPlugin, PluginApiMetricRuntime runtime) {
    super(runtime.definition().samplerId(), 250L);
    this.packId = packId;
    this.targetPlugin = targetPlugin;
    this.runtime = runtime;
  }

  public String packId() {
    return packId;
  }

  public String targetPlugin() {
    return targetPlugin;
  }

  public ReactMetricKind kind() {
    return runtime.definition().kind();
  }

  public String availabilityReason() {
    return runtime.availabilityReason(System.currentTimeMillis());
  }

  @Override
  public String getId() {
    return runtime.definition().samplerId();
  }

  @Override
  public String getName() {
    return runtime.definition().displayName();
  }

  @Override
  public Material getIcon() {
    return runtime.definition().icon();
  }

  @Override
  public void loadConfiguration() {
  }

  @Override
  public boolean reloadConfiguration() {
    return true;
  }

  @Override
  public double onSample() {
    return runtime.lastValue();
  }

  @Override
  public boolean isSampleAvailable() {
    return runtime.available(System.currentTimeMillis());
  }

  @Override
  public String formattedValue(double value) {
    return isSampleAvailable() ? Form.f(value, runtime.definition().decimals()) : "---";
  }

  @Override
  public String formattedSuffix(double value) {
    if (!isSampleAvailable()) {
      return "";
    }
    String configured = runtime.definition().unit();
    if (!configured.isEmpty()) {
      return configured;
    }
    return switch (runtime.definition().kind()) {
      case PERCENT -> "%";
      case MILLIS -> "ms";
      case BYTES -> "B";
      case RATE -> "/s";
      default -> "";
    };
  }
}
