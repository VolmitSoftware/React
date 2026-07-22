package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class BenchmarkMessages {
  public static final TextKey CPU_STARTING = TextKey.of("benchmark.cpu.starting", "<dark_red>Benchmarking CPU...</dark_red>");
  public static final TextKey DRIVE_STARTING = TextKey.of("benchmark.drive.starting", "<dark_red>Benchmarking drive...</dark_red>");
  public static final TextKey MEMORY_STARTING = TextKey.of("benchmark.memory.starting", "<dark_red>Benchmarking memory...</dark_red>");
  public static final TextKey COMPLETE = TextKey.of("benchmark.complete", "<yellow>Benchmark complete.</yellow>");
  public static final TextKey COMPLETE_LOWER_BETTER = TextKey.of("benchmark.complete_lower_better", "<yellow>Benchmark complete. Lower is better.</yellow>");
  public static final TextKey RESULT = TextKey.of("benchmark.result", "<green>Benchmark result: {rating} ({score})</green>");
  public static final TextKey CPU_RESULT = TextKey.of("benchmark.cpu.result", "<green>Benchmark result: {rating} ({milliseconds} milliseconds)</green>");
  public static final TextKey DRIVE_TEMP_FAILED = TextKey.of("benchmark.drive.temp_failed", "<red>Failed to create a temporary file for benchmarking.</red>");
  public static final TextKey DRIVE_IO_FAILED = TextKey.of("benchmark.drive.io_failed", "<red>Failed to perform drive benchmark operations.</red>");
  public static final TextKey SPEED_ULTRA_SLOW = TextKey.of("benchmark.speed.ultra_slow", "Ultra Slow");
  public static final TextKey SPEED_VERY_SLOW = TextKey.of("benchmark.speed.very_slow", "Very Slow");
  public static final TextKey SPEED_SLOW = TextKey.of("benchmark.speed.slow", "Slow");
  public static final TextKey SPEED_AVERAGE = TextKey.of("benchmark.speed.average", "Average");
  public static final TextKey SPEED_GOOD = TextKey.of("benchmark.speed.good", "Good");
  public static final TextKey SPEED_FAST = TextKey.of("benchmark.speed.fast", "Fast");
  public static final TextKey SPEED_VERY_FAST = TextKey.of("benchmark.speed.very_fast", "Very Fast");
  public static final TextKey SPEED_ULTRA_FAST = TextKey.of("benchmark.speed.ultra_fast", "Ultra Fast");
  public static final TextKey SPEED_INSANELY_FAST = TextKey.of("benchmark.speed.insanely_fast", "Insanely Fast");

  private BenchmarkMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(CPU_STARTING);
    builder.add(DRIVE_STARTING);
    builder.add(MEMORY_STARTING);
    builder.add(COMPLETE);
    builder.add(COMPLETE_LOWER_BETTER);
    builder.add(RESULT);
    builder.add(CPU_RESULT);
    builder.add(DRIVE_TEMP_FAILED);
    builder.add(DRIVE_IO_FAILED);
    builder.add(SPEED_ULTRA_SLOW);
    builder.add(SPEED_VERY_SLOW);
    builder.add(SPEED_SLOW);
    builder.add(SPEED_AVERAGE);
    builder.add(SPEED_GOOD);
    builder.add(SPEED_FAST);
    builder.add(SPEED_VERY_FAST);
    builder.add(SPEED_ULTRA_FAST);
    builder.add(SPEED_INSANELY_FAST);
  }
}
