package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class BenchmarkMessages {
  public static final TextKey HEADER = TextKey.of("benchmark.header", "&8-=[ &bReact Benchmark&8 ]=-&r");
  public static final TextKey RUNNING = TextKey.of("benchmark.running", "&7Running the {name} benchmark. The server is briefly put under load.&r");
  public static final TextKey BUSY = TextKey.of("benchmark.busy", "&cA benchmark is already running.&r");
  public static final TextKey FAILED = TextKey.of("benchmark.failed", "&cThe {name} benchmark failed: {reason}&r");
  public static final TextKey SECTION = TextKey.of("benchmark.section", "&b&l{name}&r");
  public static final TextKey LINE = TextKey.of("benchmark.line", "&8 - &7{label}&8: &f{value}&r");
  public static final TextKey LINE_SCORED = TextKey.of("benchmark.line_scored", "&8 - &7{label}&8: &f{value}&r &8[&r{bar}&8]&r &b{score}&r &8|&r {rating}");
  public static final TextKey OVERALL = TextKey.of("benchmark.overall", "&8 - &7{label}&8: &b{score}&r &8|&r {rating} &8in&r &7{duration}&r");
  public static final TextKey SCALE = TextKey.of("benchmark.scale", "&8A score of 100 matches the reference machine. Higher is better.&r");

  public static final TextKey NAME_CPU = TextKey.of("benchmark.name.cpu", "Processor");
  public static final TextKey NAME_MEMORY = TextKey.of("benchmark.name.memory", "Memory");
  public static final TextKey NAME_DRIVE = TextKey.of("benchmark.name.drive", "Drive");

  public static final TextKey LABEL_OVERALL = TextKey.of("benchmark.label.overall", "Overall");
  public static final TextKey LABEL_THREADS = TextKey.of("benchmark.label.threads", "Threads");
  public static final TextKey LABEL_SAMPLE = TextKey.of("benchmark.label.sample", "Sample");
  public static final TextKey LABEL_CPU_INTEGER = TextKey.of("benchmark.label.cpu.integer", "Integer");
  public static final TextKey LABEL_CPU_FLOATING = TextKey.of("benchmark.label.cpu.floating", "Floating point");
  public static final TextKey LABEL_CPU_CACHE = TextKey.of("benchmark.label.cpu.cache", "Cache walk");
  public static final TextKey LABEL_CPU_MULTI_CORE = TextKey.of("benchmark.label.cpu.multi_core", "All cores");
  public static final TextKey LABEL_CPU_SCALING = TextKey.of("benchmark.label.cpu.scaling", "Thread scaling");
  public static final TextKey LABEL_MEMORY_WRITE = TextKey.of("benchmark.label.memory.write", "Write bandwidth");
  public static final TextKey LABEL_MEMORY_READ = TextKey.of("benchmark.label.memory.read", "Read bandwidth");
  public static final TextKey LABEL_MEMORY_COPY = TextKey.of("benchmark.label.memory.copy", "Copy bandwidth");
  public static final TextKey LABEL_MEMORY_LATENCY = TextKey.of("benchmark.label.memory.latency", "Random access");
  public static final TextKey LABEL_MEMORY_WORKING_SET = TextKey.of("benchmark.label.memory.working_set", "Working set");
  public static final TextKey LABEL_DRIVE_WRITE = TextKey.of("benchmark.label.drive.write", "Flushed write");
  public static final TextKey LABEL_DRIVE_FLUSH = TextKey.of("benchmark.label.drive.flush", "Flush latency");
  public static final TextKey LABEL_DRIVE_READ = TextKey.of("benchmark.label.drive.read", "Buffered read");
  public static final TextKey LABEL_DRIVE_RANDOM_READ = TextKey.of("benchmark.label.drive.random_read", "Buffered 4K read");
  public static final TextKey LABEL_DRIVE_TARGET = TextKey.of("benchmark.label.drive.target", "Target");

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
    builder.add(HEADER);
    builder.add(RUNNING);
    builder.add(BUSY);
    builder.add(FAILED);
    builder.add(SECTION);
    builder.add(LINE);
    builder.add(LINE_SCORED);
    builder.add(OVERALL);
    builder.add(SCALE);
    builder.add(NAME_CPU);
    builder.add(NAME_MEMORY);
    builder.add(NAME_DRIVE);
    builder.add(LABEL_OVERALL);
    builder.add(LABEL_THREADS);
    builder.add(LABEL_SAMPLE);
    builder.add(LABEL_CPU_INTEGER);
    builder.add(LABEL_CPU_FLOATING);
    builder.add(LABEL_CPU_CACHE);
    builder.add(LABEL_CPU_MULTI_CORE);
    builder.add(LABEL_CPU_SCALING);
    builder.add(LABEL_MEMORY_WRITE);
    builder.add(LABEL_MEMORY_READ);
    builder.add(LABEL_MEMORY_COPY);
    builder.add(LABEL_MEMORY_LATENCY);
    builder.add(LABEL_MEMORY_WORKING_SET);
    builder.add(LABEL_DRIVE_WRITE);
    builder.add(LABEL_DRIVE_FLUSH);
    builder.add(LABEL_DRIVE_READ);
    builder.add(LABEL_DRIVE_RANDOM_READ);
    builder.add(LABEL_DRIVE_TARGET);
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
