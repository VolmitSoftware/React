package art.arcane.react.core.telemetry;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.function.Supplier;

public final class HostMetricSampler implements Sampler {
  private final HostMetric metric;
  private final Supplier<HostTelemetrySnapshot> snapshots;
  private volatile boolean active;

  public HostMetricSampler(HostMetric metric, Supplier<HostTelemetrySnapshot> snapshots) {
    this.metric = metric;
    this.snapshots = snapshots;
  }

  @Override
  public String getId() {
    return metric.definition().id();
  }

  @Override
  public String getName() {
    return metric.definition().name();
  }

  @Override
  public Material getIcon() {
    return switch (metric) {
      case PHYSICAL_USED -> Material.WATER_BUCKET;
      case PHYSICAL_FREE -> Material.BUCKET;
      case UPTIME -> Material.CLOCK;
      default -> switch (metric.definition().source()) {
        case STORAGE -> Material.IRON_PICKAXE;
        case NETWORK -> Material.OBSERVER;
        case JVM, GC -> Material.EXPERIENCE_BOTTLE;
        case THREADS -> Material.STRING;
        default -> Material.COMPARATOR;
      };
    };
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
    return active ? snapshots.get().reading(metric).value() : 0D;
  }

  @Override
  public Reading captureReading() {
    HostReading reading = snapshots.get().reading(metric).fresh(metric, System.currentTimeMillis());
    return (active ? reading : reading.unavailable("stopped")).reading(metric);
  }

  @Override
  public Metadata metadata() {
    return metric.metadata();
  }

  @Override
  public boolean isSampleAvailable() {
    return active && snapshots.get().reading(metric).fresh(metric, System.currentTimeMillis()).available();
  }

  @Override
  public String formattedValue(double value) {
    if (!isSampleAvailable()) {
      return "---";
    }
    return switch (metric.definition().format()) {
      case COUNT -> Form.f(Math.rint(value));
      case DECIMAL, PERCENT -> Form.f(value, 2);
      case BYTES, BYTES_PER_SECOND -> Form.memSizeSplit(Math.max(0L, (long) value), 1)[0];
    };
  }

  @Override
  public String formattedSuffix(double value) {
    if (!isSampleAvailable()) {
      return "";
    }
    return switch (metric.definition().format()) {
      case BYTES -> Form.memSizeSplit(Math.max(0L, (long) value), 1)[1];
      case BYTES_PER_SECOND -> Form.memSizeSplit(Math.max(0L, (long) value), 1)[1] + "/s";
      default -> metric.definition().unit();
    };
  }

  @Override
  public void start() {
    active = true;
  }

  @Override
  public void stop() {
    active = false;
  }
}
