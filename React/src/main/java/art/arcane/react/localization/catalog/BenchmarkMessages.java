package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class BenchmarkMessages {
  public static final TextKey HEADER = TextKey.of("benchmark.header", "<dark_gray>-=[ </dark_gray><aqua>React Benchmark</aqua><dark_gray> ]=-</dark_gray>");
  public static final TextKey RUNNING = TextKey.of("benchmark.running", "<gray>Running the {name} benchmark. The server is briefly put under load.</gray>");
  public static final TextKey BUSY = TextKey.of("benchmark.busy", "<red>A benchmark is already running.</red>");
  public static final TextKey FAILED = TextKey.of("benchmark.failed", "<red>The {name} benchmark failed: {reason}</red>");
  public static final TextKey SECTION = TextKey.of("benchmark.section", "<bold><aqua>{name}</aqua></bold>");
  public static final TextKey LINE = TextKey.of("benchmark.line", "<dark_gray> - </dark_gray><gray>{label}</gray><dark_gray>: </dark_gray><white>{value}</white>");
  public static final TextKey LINE_SCORED = TextKey.of("benchmark.line_scored", "<dark_gray> - </dark_gray><gray>{label}</gray><dark_gray>: </dark_gray><white>{value}</white> <dark_gray>[</dark_gray>{bar}<dark_gray>]</dark_gray> <aqua>{score}</aqua> <dark_gray>|</dark_gray> {rating}");
  public static final TextKey OVERALL = TextKey.of("benchmark.overall", "<dark_gray> - </dark_gray><gray>{label}</gray><dark_gray>: </dark_gray><aqua>{score}</aqua> <dark_gray>|</dark_gray> {rating} <dark_gray>in</dark_gray> <gray>{duration}</gray>");
  public static final TextKey SCALE = TextKey.of("benchmark.scale", "<dark_gray>A score of 100 matches the reference machine. Higher is better.</dark_gray>");

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
