package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.core.controller.TelemetryController;
import art.arcane.react.core.telemetry.HostMetric;
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.function.Supplier;

public class SamplerJvmThreads extends ReactCachedSampler {
  public static final String ID = "jvm-threads";
  private transient final Supplier<HostTelemetrySnapshot> host;

  public SamplerJvmThreads() {
    this(TelemetryController::currentHostSnapshot);
  }

  SamplerJvmThreads(Supplier<HostTelemetrySnapshot> host) {
    super(ID, 1000L);
    this.host = host;
  }

  @Override
  public Material getIcon() {
    return Material.STRING;
  }

  @Override
  public Metadata metadata() {
    return HostMetric.THREAD_LIVE.metadata();
  }

  @Override
  public boolean isSampleAvailable() {
    return host.get().reading(HostMetric.THREAD_LIVE).fresh(HostMetric.THREAD_LIVE, System.currentTimeMillis()).available();
  }

  @Override
  public Reading captureReading() {
    return host.get().reading(HostMetric.THREAD_LIVE).fresh(HostMetric.THREAD_LIVE, System.currentTimeMillis()).reading(HostMetric.THREAD_LIVE);
  }

  @Override
  public double onSample() {
    return host.get().reading(HostMetric.THREAD_LIVE).value();
  }

  @Override
  public String formattedValue(double value) {
    return Form.f(Math.round(value));
  }

  @Override
  public String formattedSuffix(double value) {
    return " threads";
  }
}
