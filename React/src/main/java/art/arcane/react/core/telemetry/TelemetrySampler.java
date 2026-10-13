package art.arcane.react.core.telemetry;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

public final class TelemetrySampler implements Sampler {
  private final Options options;
  private volatile boolean active;

  public TelemetrySampler(Options options) {
    this.options = options;
  }

  @Override
  public String getId() {
    return options.id();
  }

  @Override
  public String getName() {
    return options.name();
  }

  @Override
  public Material getIcon() {
    return options.icon();
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
    return active ? options.valueSupplier().getAsDouble() : 0D;
  }

  @Override
  public Reading captureReading() {
    long sampledAtMs = options.sampledAtSupplier().getAsLong();
    return reading(sample(), sampledAtMs, isSampleAvailable());
  }

  @Override
  public Metadata metadata() {
    long interval = switch (options.id()) {
      case "react-history-disk-bytes", "react-history-wal-bytes" -> 30_000L;
      default -> 1_000L;
    };
    String unit = switch (options.format()) {
      case BYTES -> "bytes";
      case BYTES_PER_SECOND -> "bytes/s";
      case PERCENT -> "%";
      case COUNT, DECIMAL -> options.suffix().isEmpty() ? "count" : options.suffix();
    };
    long window = options.id().startsWith("react-nearby-") || options.id().startsWith("react-heatmap-")
        || options.id().startsWith("react-map-") || options.id().startsWith("react-entity-scan-") ? 1_000L
        : options.suffix().equals("/min") ? 60_000L : 0L;
    return new Metadata(interval, window, Quality.MEASURED, unit);
  }

  @Override
  public boolean isSampleAvailable() {
    return active && options.availabilitySupplier().getAsBoolean();
  }

  @Override
  public String formattedValue(double value) {
    if (!isSampleAvailable()) {
      return "---";
    }
    return switch (options.format()) {
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
    return switch (options.format()) {
      case BYTES -> Form.memSizeSplit(Math.max(0L, (long) value), 1)[1];
      case BYTES_PER_SECOND -> Form.memSizeSplit(Math.max(0L, (long) value), 1)[1] + "/s";
      default -> options.suffix();
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

  public enum Format {
    COUNT,
    DECIMAL,
    BYTES,
    BYTES_PER_SECOND,
    PERCENT
  }

  public record Options(
      String id,
      String name,
      String suffix,
      Material icon,
      DoubleSupplier valueSupplier,
      BooleanSupplier availabilitySupplier,
      Format format,
      LongSupplier sampledAtSupplier
  ) {
  }
}
