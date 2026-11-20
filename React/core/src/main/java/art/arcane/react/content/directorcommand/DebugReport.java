package art.arcane.react.content.directorcommand;

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The text of {@code /react debug dump} on platforms without VolmLib's Bukkit dump. It follows the Bukkit report
 * section for section, naming the owner with the platform's noun ("Mod" or "Plugin").
 */
public final class DebugReport {
  private static final int BUFFER_SIZE = 16 * 1024;
  private static final int MAX_KNOWN_FILES = 32;
  private static final long MAX_KNOWN_FILE_BYTES = 16L * 1024L * 1024L;
  private static final long MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L;
  private static final List<String> CONFIGURATION_EXTENSIONS = List.of(".toml", ".yml", ".yaml", ".json", ".properties");

  private DebugReport() {
  }

  public record Field(String name, String value) {
    public Field {
      Objects.requireNonNull(name, "name");
      value = Objects.requireNonNullElse(value, "unavailable");
    }

    public static Field of(String name, Object value) {
      return new Field(name, Objects.toString(value, "unavailable"));
    }
  }

  public record Owner(String name, String version, List<Field> attributes) {
    public Owner {
      Objects.requireNonNull(name, "name");
      version = Objects.requireNonNullElse(version, "unknown");
      attributes = List.copyOf(attributes);
    }
  }

  /**
   * One capture of server state. {@code ownerNoun} is the title-case noun for React's own kind of add-on;
   * {@code codeSource} may be null when the platform hides React's jar.
   */
  public record Snapshot(
      Instant generatedAt,
      String name,
      String version,
      String ownerNoun,
      String senderType,
      List<Field> server,
      List<Owner> owners,
      Path dataDirectory,
      Path codeSource
  ) {
    public Snapshot {
      Objects.requireNonNull(generatedAt, "generatedAt");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(version, "version");
      Objects.requireNonNull(ownerNoun, "ownerNoun");
      Objects.requireNonNull(senderType, "senderType");
      server = List.copyOf(server);
      owners = List.copyOf(owners);
      dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
    }
  }

  public static String create(Snapshot snapshot, String details) {
    Objects.requireNonNull(snapshot, "snapshot");
    String noun = snapshot.ownerNoun();
    StringBuilder report = new StringBuilder(24_576);
    section(report, snapshot.name() + " diagnostic report");
    value(report, "Generated (UTC)", snapshot.generatedAt());
    value(report, "Format", 1);
    value(report, noun, snapshot.name());
    value(report, "Version", snapshot.version());
    value(report, "Command sender type", snapshot.senderType());
    section(report, "Server");
    for (Field field : snapshot.server()) {
      value(report, field.name(), field.value());
    }
    if (details != null && !details.isBlank()) {
      report.append('\n').append(details);
      if (report.charAt(report.length() - 1) != '\n') {
        report.append('\n');
      }
    }
    appendOwners(report, noun + "s", snapshot.owners());
    appendRuntime(report);
    appendMemory(report);
    appendCpu(report);
    appendGarbageCollectors(report);
    appendBufferPools(report);
    appendStorage(report, noun + " data filesystem", snapshot.dataDirectory());
    section(report, noun + " configuration files");
    report.append(describeFiles(snapshot.dataDirectory(), configurationFiles(snapshot.dataDirectory())));
    appendArtifact(report, noun + " artifact", snapshot.codeSource());
    return report.toString();
  }

  /**
   * Top-level configuration files of {@code root}, sorted by name. Empty when the directory cannot be read.
   */
  public static List<Path> configurationFiles(Path root) {
    List<Path> files = new ArrayList<>();
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
      return files;
    }
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        String name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
        if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) && hasConfigurationExtension(name)) {
          files.add(entry.getFileName());
        }
      }
    } catch (IOException | RuntimeException unreadable) {
      return List.of();
    }
    files.sort((Path left, Path right) -> left.toString().compareToIgnoreCase(right.toString()));
    return files;
  }

  private static boolean hasConfigurationExtension(String name) {
    for (String extension : CONFIGURATION_EXTENSIONS) {
      if (name.endsWith(extension)) {
        return true;
      }
    }
    return false;
  }

  public static String describeFiles(Path root, List<Path> relativePaths) {
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(relativePaths, "relativePaths");
    StringBuilder report = new StringBuilder(1024);
    int limit = Math.min(MAX_KNOWN_FILES, relativePaths.size());
    for (int index = 0; index < limit; index++) {
      appendKnownFile(report, root, Objects.requireNonNull(relativePaths.get(index), "relative path"));
    }
    if (relativePaths.size() > limit) {
      value(report, "Remaining files", relativePaths.size() - limit);
    }
    return report.toString();
  }

  private static void appendOwners(StringBuilder report, String title, List<Owner> owners) {
    section(report, title);
    value(report, "Loaded", owners.size());
    for (Owner owner : owners) {
      report.append("- ").append(sanitize(owner.name())).append(' ').append(sanitize(owner.version()));
      for (Field attribute : owner.attributes()) {
        report.append(" | ").append(sanitize(attribute.name())).append('=').append(sanitize(attribute.value()));
      }
      report.append('\n');
    }
  }

  private static void appendRuntime(StringBuilder report) {
    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
    CompilationMXBean compilation = ManagementFactory.getCompilationMXBean();
    section(report, "Java runtime");
    value(report, "Java version", System.getProperty("java.version"));
    value(report, "Java vendor", System.getProperty("java.vendor"));
    value(report, "VM name", System.getProperty("java.vm.name"));
    value(report, "VM vendor", System.getProperty("java.vm.vendor"));
    value(report, "VM version", System.getProperty("java.vm.version"));
    value(report, "Process ID", runtime.getPid());
    value(report, "Uptime", duration(runtime.getUptime()));
    value(report, "Start time (epoch ms)", runtime.getStartTime());
    value(report, "Default locale", Locale.getDefault().toLanguageTag());
    value(report, "Default time zone", ZoneId.systemDefault());
    value(report, "Default charset", Charset.defaultCharset());
    value(report, "Native encoding", System.getProperty("native.encoding", "unavailable"));
    value(report, "Loaded classes", classes.getLoadedClassCount());
    value(report, "Total loaded classes", classes.getTotalLoadedClassCount());
    value(report, "Unloaded classes", classes.getUnloadedClassCount());
    if (compilation != null) {
      value(report, "JIT compiler", compilation.getName());
      if (compilation.isCompilationTimeMonitoringSupported()) {
        value(report, "JIT compilation time", duration(compilation.getTotalCompilationTime()));
      }
    }
  }

  private static void appendMemory(StringBuilder report) {
    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    Runtime runtime = Runtime.getRuntime();
    section(report, "Memory");
    memoryUsage(report, "Heap", memory.getHeapMemoryUsage());
    memoryUsage(report, "Non-heap", memory.getNonHeapMemoryUsage());
    value(report, "Runtime used", bytes(runtime.totalMemory() - runtime.freeMemory()));
    value(report, "Runtime free", bytes(runtime.freeMemory()));
    value(report, "Runtime total", bytes(runtime.totalMemory()));
    value(report, "Runtime maximum", bytes(runtime.maxMemory()));
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      memoryUsage(report, "Pool " + pool.getName(), pool.getUsage());
    }
  }

  private static void appendCpu(StringBuilder report) {
    java.lang.management.OperatingSystemMXBean system = ManagementFactory.getOperatingSystemMXBean();
    section(report, "CPU and operating system");
    value(report, "OS name", System.getProperty("os.name"));
    value(report, "OS version", System.getProperty("os.version"));
    value(report, "OS architecture", System.getProperty("os.arch"));
    value(report, "Logical processors", system.getAvailableProcessors());
    value(report, "System load average", decimal(system.getSystemLoadAverage()));
    if (system instanceof OperatingSystemMXBean operatingSystem) {
      value(report, "Process CPU load", percent(operatingSystem.getProcessCpuLoad()));
      value(report, "System CPU load", percent(operatingSystem.getCpuLoad()));
      value(report, "Process CPU time", duration(operatingSystem.getProcessCpuTime() / 1_000_000L));
      value(report, "Committed virtual memory", bytes(operatingSystem.getCommittedVirtualMemorySize()));
      value(report, "Physical memory total", bytes(operatingSystem.getTotalMemorySize()));
      value(report, "Physical memory free", bytes(operatingSystem.getFreeMemorySize()));
      value(report, "Swap total", bytes(operatingSystem.getTotalSwapSpaceSize()));
      value(report, "Swap free", bytes(operatingSystem.getFreeSwapSpaceSize()));
    }
    if (system instanceof UnixOperatingSystemMXBean unix) {
      value(report, "Open file descriptors", unix.getOpenFileDescriptorCount());
      value(report, "Maximum file descriptors", unix.getMaxFileDescriptorCount());
    }
  }

  private static void appendGarbageCollectors(StringBuilder report) {
    section(report, "Garbage collectors");
    for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
      value(report, collector.getName() + " collections", collector.getCollectionCount());
      value(report, collector.getName() + " time", duration(collector.getCollectionTime()));
    }
  }

  private static void appendBufferPools(StringBuilder report) {
    section(report, "Buffer pools");
    for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
      value(report, pool.getName() + " count", pool.getCount());
      value(report, pool.getName() + " used", bytes(pool.getMemoryUsed()));
      value(report, pool.getName() + " capacity", bytes(pool.getTotalCapacity()));
    }
  }

  private static void appendStorage(StringBuilder report, String title, Path dataDirectory) {
    section(report, title);
    try {
      FileStore store = Files.getFileStore(dataDirectory);
      value(report, "Type", store.type());
      value(report, "Total", bytes(store.getTotalSpace()));
      value(report, "Usable", bytes(store.getUsableSpace()));
      value(report, "Unallocated", bytes(store.getUnallocatedSpace()));
    } catch (IOException | RuntimeException exception) {
      value(report, "Status", "unavailable (" + exception.getClass().getSimpleName() + ")");
    }
  }

  private static void appendKnownFile(StringBuilder report, Path root, Path relative) {
    String label = relative.toString().replace('\\', '/');
    Path normalizedRoot = root.toAbsolutePath().normalize();
    Path target = normalizedRoot.resolve(relative).normalize();
    if (relative.isAbsolute() || !target.startsWith(normalizedRoot)) {
      value(report, "File", "invalid relative path");
      return;
    }
    try {
      if (hasSymbolicLink(normalizedRoot, target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
        value(report, label, "not present");
        return;
      }
      value(report, label, "size=" + bytes(Files.size(target))
          + ", modified=" + Files.getLastModifiedTime(target, LinkOption.NOFOLLOW_LINKS).toInstant()
          + ", sha256=" + sha256(target, MAX_KNOWN_FILE_BYTES));
    } catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
      value(report, label, "unavailable (" + exception.getClass().getSimpleName() + ")");
    }
  }

  private static boolean hasSymbolicLink(Path root, Path target) {
    Path current = root;
    if (Files.isSymbolicLink(current)) {
      return true;
    }
    for (Path part : root.relativize(target)) {
      current = current.resolve(part);
      if (Files.isSymbolicLink(current)) {
        return true;
      }
    }
    return false;
  }

  private static void appendArtifact(StringBuilder report, String title, Path codeSource) {
    section(report, title);
    if (codeSource == null) {
      value(report, "Status", "code source unavailable");
      return;
    }
    value(report, "Filename", codeSource.getFileName());
    value(report, "Type", Files.isRegularFile(codeSource) ? "jar" : Files.isDirectory(codeSource) ? "classes directory" : "unknown");
    if (!Files.isRegularFile(codeSource)) {
      return;
    }
    try {
      value(report, "Size", bytes(Files.size(codeSource)));
      value(report, "Modified", Files.getLastModifiedTime(codeSource, LinkOption.NOFOLLOW_LINKS).toInstant());
      value(report, "SHA-256", sha256(codeSource, MAX_ARTIFACT_BYTES));
    } catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
      value(report, "Status", "artifact inspection unavailable (" + exception.getClass().getSimpleName() + ")");
    }
  }

  private static String sha256(Path file, long maximumBytes) throws IOException, NoSuchAlgorithmException {
    if (Files.size(file) > maximumBytes) {
      return "not computed (file exceeds " + bytes(maximumBytes) + ")";
    }
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] buffer = new byte[BUFFER_SIZE];
    long total = 0L;
    try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
      int read;
      while ((read = input.read(buffer)) >= 0) {
        total += read;
        if (total > maximumBytes) {
          return "not computed (file exceeds " + bytes(maximumBytes) + ")";
        }
        if (read > 0) {
          digest.update(buffer, 0, read);
        }
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void memoryUsage(StringBuilder report, String label, MemoryUsage usage) {
    if (usage == null) {
      value(report, label, "unavailable");
      return;
    }
    value(report, label, "used=" + bytes(usage.getUsed()) + ", committed=" + bytes(usage.getCommitted())
        + ", maximum=" + bytes(usage.getMax()));
  }

  private static void section(StringBuilder report, String name) {
    if (report.length() > 0) {
      report.append('\n');
    }
    report.append("== ").append(sanitize(name)).append(" ==\n");
  }

  private static void value(StringBuilder report, String name, Object value) {
    report.append(sanitize(name)).append(": ").append(sanitize(Objects.toString(value, "unavailable"))).append('\n');
  }

  static String bytes(long value) {
    if (value < 0L) {
      return "unavailable";
    }
    String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
    double scaled = value;
    int unit = 0;
    while (scaled >= 1024D && unit < units.length - 1) {
      scaled /= 1024D;
      unit++;
    }
    return String.format(Locale.ROOT, "%.2f %s (%d bytes)", scaled, units[unit], value);
  }

  private static String percent(double value) {
    return value < 0D || !Double.isFinite(value) ? "unavailable" : String.format(Locale.ROOT, "%.2f%%", value * 100D);
  }

  private static String decimal(double value) {
    return value < 0D || !Double.isFinite(value) ? "unavailable" : String.format(Locale.ROOT, "%.3f", value);
  }

  static String duration(long millis) {
    if (millis < 0L) {
      return "unavailable";
    }
    Duration duration = Duration.ofMillis(millis);
    return duration.toDaysPart() + "d " + duration.toHoursPart() + "h " + duration.toMinutesPart()
        + "m " + duration.toSecondsPart() + "s " + duration.toMillisPart() + "ms";
  }

  static String sanitize(String value) {
    return Objects.requireNonNullElse(value, "unavailable")
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace('\t', ' ')
        .trim();
  }
}
