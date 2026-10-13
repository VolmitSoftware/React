package art.arcane.react.core.telemetry.runtime;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.function.Supplier;

public final class RuntimeMetricSampler implements Sampler {
  private final Definition definition;
  private final Supplier<RuntimeMetric> source;
  private volatile boolean active;

  public RuntimeMetricSampler(Definition definition, Supplier<RuntimeMetric> source) {
    this.definition = definition;
    this.source = source;
  }

  @Override
  public String getId() {
    return definition.id();
  }

  @Override
  public String getName() {
    return definition.name();
  }

  @Override
  public Material getIcon() {
    return Material.COMPASS;
  }

  @Override
  public void loadConfiguration() {
  }

  @Override
  public boolean reloadConfiguration() {
    return true;
  }

  @Override
  public double sample() {
    return active ? source.get().value() : 0D;
  }

  @Override
  public Reading captureReading() {
    RuntimeMetric value = source.get();
    return reading(value.value(), value.sampledAtMs(), active && value.available(), active ? value.unavailableReason() : "stopped");
  }

  @Override
  public Metadata metadata() {
    return new Metadata(1_000L, definition.windowMs(), Quality.MEASURED, definition.unit());
  }

  @Override
  public String availabilityReason() {
    return active ? source.get().unavailableReason() : "stopped";
  }

  @Override
  public boolean isSampleAvailable() {
    return active && source.get().available();
  }

  @Override
  public String formattedValue(double value) {
    return isSampleAvailable() ? Form.f(value, 2) : "---";
  }

  @Override
  public String formattedSuffix(double value) {
    return isSampleAvailable() ? definition.unit() : "";
  }

  @Override
  public void start() {
    active = true;
  }

  @Override
  public void stop() {
    active = false;
  }

  public record Definition(String id, String name, String unit, long windowMs) {
  }
}
