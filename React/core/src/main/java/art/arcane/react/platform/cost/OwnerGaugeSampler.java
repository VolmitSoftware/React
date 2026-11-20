package art.arcane.react.platform.cost;

import art.arcane.react.api.sampler.ReactCachedSampler;

import java.util.function.ToDoubleFunction;

/** One owner's reading of an {@link OwnerGauge}, read from the latest snapshot its source published. */
public final class OwnerGaugeSampler extends ReactCachedSampler {
  private final transient OwnerGauge gauge;
  private final transient String owner;
  private final transient String name;
  private final transient ToDoubleFunction<String> values;

  public OwnerGaugeSampler(OwnerGauge gauge, String owner, String displayName, ToDoubleFunction<String> values) {
    super(gauge.samplerId(owner), gauge.intervalMs());
    this.gauge = gauge;
    this.owner = owner;
    this.name = (displayName == null || displayName.isBlank() ? owner : displayName) + " " + gauge.label();
    this.values = values;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getIcon() {
    return gauge.icon();
  }

  @Override
  public double onSample() {
    return values.applyAsDouble(owner);
  }

  @Override
  public String format(double t) {
    return formattedValue(t) + formattedSuffix(t);
  }

  @Override
  public String formattedValue(double t) {
    return gauge.formattedValue(t);
  }

  @Override
  public String formattedSuffix(double t) {
    return gauge.formattedSuffix(t);
  }

  @Override
  public void loadConfiguration() {
  }

  @Override
  public boolean reloadConfiguration() {
    return true;
  }
}
