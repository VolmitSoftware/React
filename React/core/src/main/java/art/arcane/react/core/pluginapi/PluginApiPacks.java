package art.arcane.react.core.pluginapi;

import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.api.web.WebRevisions;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime.PackStatus;
import art.arcane.react.platform.PlatformLog;
import art.arcane.react.util.common.scheduling.J;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * The API pack catalog: scans the pack folder off-thread, runs each pack's samplers and collects them on the server
 * thread. The platform calls {@link #tick()} about once a second between {@link #start} and {@link #stop}.
 */
public final class PluginApiPacks {
  public static final int MAX_PACKS = 32;
  public static final int MAX_TOTAL_METRICS = 256;
  private static final long SCAN_INTERVAL_MS = 3_000L;
  private static volatile PluginApiPacks active;

  private final PluginApiHost host;
  private final AtomicBoolean collectionQueued = new AtomicBoolean();
  private final AtomicBoolean running = new AtomicBoolean();
  private final AtomicBoolean scanQueued = new AtomicBoolean();
  private final Map<String, PluginApiPackRuntime> packs = new LinkedHashMap<>();
  private final Map<String, String> validationErrors = new LinkedHashMap<>();
  private final Set<String> collectFailures = new HashSet<>();
  private ExecutorService ioExecutor;
  private Path packFolder;
  private long lastScanMs;

  public PluginApiPacks(PluginApiHost host) {
    this.host = host;
  }

  /** The running catalog, or null while React is stopped. */
  public static PluginApiPacks active() {
    return active;
  }

  public PluginApiHost host() {
    return host;
  }

  public void start(Path dataFolder) {
    running.set(true);
    packFolder = dataFolder.resolve(host.folderName()).toAbsolutePath().normalize();
    ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "React-PluginApi-IO");
      thread.setDaemon(true);
      return thread;
    });
    lastScanMs = 0L;
    active = this;
  }

  public void stop() {
    if (active == this) {
      active = null;
    }
    running.set(false);
    ExecutorService executor = ioExecutor;
    ioExecutor = null;
    if (executor != null) {
      executor.shutdownNow();
    }
    SampleController sampleController = sampleController();
    synchronized (packs) {
      for (PluginApiPackRuntime runtime : packs.values()) {
        runtime.retire(sampleController);
      }
      packs.clear();
      validationErrors.clear();
      collectFailures.clear();
    }
  }

  public void tick() {
    if (collectionQueued.compareAndSet(false, true)) {
      J.s(() -> {
        try {
          collectOnServerThread();
        } finally {
          collectionQueued.set(false);
        }
      });
    }
    long now = System.currentTimeMillis();
    if (now - lastScanMs >= SCAN_INTERVAL_MS) {
      lastScanMs = now;
      requestReload();
    }
  }

  public void requestReload() {
    ExecutorService executor = ioExecutor;
    if (!running.get() || executor == null || !scanQueued.compareAndSet(false, true)) {
      return;
    }
    executor.execute(() -> {
      try {
        if (!running.get()) {
          return;
        }
        installBundledExamples();
        PreparedCatalog prepared = prepareCatalog();
        if (running.get()) {
          J.s(() -> {
            if (running.get()) {
              applyCatalog(prepared);
            }
          });
        }
      } catch (Throwable failure) {
        PlatformLog.warn(host.label() + " pack scan failed", failure);
      } finally {
        scanQueued.set(false);
      }
    });
  }

  public List<PackStatus> statuses() {
    synchronized (packs) {
      return packs.values().stream()
          .map(PluginApiPackRuntime::snapshot)
          .sorted(Comparator.comparing(PackStatus::id))
          .toList();
    }
  }

  public PackStatus status(String id) {
    synchronized (packs) {
      PluginApiPackRuntime runtime = packs.get(normalizeId(id));
      return runtime == null ? null : runtime.snapshot();
    }
  }

  public Map<String, String> validationErrors() {
    synchronized (packs) {
      return Map.copyOf(validationErrors);
    }
  }

  public Path packFolder() {
    return packFolder;
  }

  public PluginApiPackDefinition parse(Path sourcePath, String rawContent) throws IOException {
    return PluginApiPackParser.parse(sourcePath, rawContent, host::icon);
  }

  public ValidationResult validate(String rawContent) {
    try {
      PluginApiPackDefinition definition = parse(Path.of("validation.toml"), rawContent);
      return new ValidationResult(true, definition.id(), definition.metrics().size(), "valid");
    } catch (IOException failure) {
      return new ValidationResult(false, "", 0, failure.getMessage());
    }
  }

  public PackStatus install(String requestedId, String rawContent) throws IOException {
    PluginApiPackDefinition definition = parse(Path.of("upload.toml"), rawContent);
    String normalizedRequestedId = normalizeId(requestedId);
    if (!definition.id().equals(normalizedRequestedId)) {
      throw new IOException("Path id does not match pack id");
    }
    Files.createDirectories(packFolder);
    Path target = existingPackPath(definition.id());
    Path temporary = Files.createTempFile(packFolder, definition.id() + "-", ".tmp");
    try {
      Files.writeString(temporary, rawContent, StandardCharsets.UTF_8);
      try {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
    requestReload();
    return new PackStatus(
        definition.id(),
        definition.version(),
        definition.name(),
        definition.authors(),
        definition.targetPlugin(),
        "",
        definition.targetVersions(),
        definition.enabled(),
        definition.trusted(),
        PluginApiPackRuntime.PackState.LOADING,
        "reload-queued",
        target.getFileName().toString(),
        rawContent,
        List.of()
    );
  }

  public boolean remove(String id) throws IOException {
    String normalizedId = normalizeId(id);
    PluginApiPackRuntime removed;
    synchronized (packs) {
      removed = packs.get(normalizedId);
    }
    if (removed == null) {
      return false;
    }
    Path sourcePath = removed.definition().sourcePath().toAbsolutePath().normalize();
    if (!sourcePath.startsWith(packFolder)) {
      throw new IOException("Pack source is outside the canonical folder");
    }
    Files.deleteIfExists(sourcePath);
    J.sResult(() -> {
      synchronized (packs) {
        if (packs.get(normalizedId) != removed) {
          return false;
        }
        packs.remove(normalizedId);
        collectFailures.remove(normalizedId);
      }
      removed.retire(sampleController());
      return true;
    });
    WebRevisions.bump(WebRevisions.Kind.PACKS);
    return true;
  }

  void put(String id, PluginApiPackRuntime runtime) {
    synchronized (packs) {
      packs.put(id, runtime);
    }
  }

  void collectOnServerThread() {
    long now = System.currentTimeMillis();
    synchronized (packs) {
      for (Map.Entry<String, PluginApiPackRuntime> entry : packs.entrySet()) {
        collectPack(entry.getKey(), entry.getValue(), now);
      }
    }
  }

  private void collectPack(String id, PluginApiPackRuntime runtime, long now) {
    try {
      runtime.collect(now);
      collectFailures.remove(id);
    } catch (RuntimeException | LinkageError failure) {
      if (collectFailures.add(id)) {
        PlatformLog.reportError(host.label() + " pack " + id + " collection failed", failure);
      }
    }
  }

  private PreparedCatalog prepareCatalog() throws IOException {
    if (!Files.isDirectory(packFolder)) {
      Files.createDirectories(packFolder);
    }
    List<Path> files;
    try (Stream<Path> stream = Files.list(packFolder)) {
      files = stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".toml"))
          .sorted()
          .limit(MAX_PACKS + 1L)
          .toList();
    }
    Map<String, PluginApiPackDefinition> definitions = new LinkedHashMap<>();
    Map<String, String> errors = new LinkedHashMap<>();
    Set<Path> presentPaths = new HashSet<>();
    Map<String, Path> idPaths = new HashMap<>();
    int totalMetrics = 0;
    if (files.size() > MAX_PACKS) {
      errors.put("catalog", "Pack count exceeds " + MAX_PACKS);
      files = files.subList(0, MAX_PACKS);
    }
    for (Path file : files) {
      Path normalized = file.toAbsolutePath().normalize();
      presentPaths.add(normalized);
      try {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        PluginApiPackDefinition definition = parse(normalized, raw);
        Path previous = idPaths.putIfAbsent(definition.id(), normalized);
        if (previous != null) {
          PluginApiPackDefinition removed = definitions.remove(definition.id());
          if (removed != null) {
            totalMetrics -= removed.metrics().size();
          }
          errors.put(previous.getFileName().toString(), "Duplicate pack id: " + definition.id());
          errors.put(file.getFileName().toString(), "Duplicate pack id: " + definition.id());
          continue;
        }
        totalMetrics += definition.metrics().size();
        if (totalMetrics > MAX_TOTAL_METRICS) {
          errors.put(file.getFileName().toString(), "Catalog metric count exceeds " + MAX_TOTAL_METRICS);
          continue;
        }
        definitions.put(definition.id(), definition);
      } catch (IOException | RuntimeException failure) {
        String message = failure.getMessage();
        errors.put(
            file.getFileName().toString(),
            message == null || message.isBlank() ? failure.getClass().getSimpleName() : message
        );
      }
    }
    return new PreparedCatalog(definitions, errors, presentPaths);
  }

  void applyCatalog(PreparedCatalog prepared) {
    SampleController sampleController = sampleController();
    boolean changed;
    synchronized (packs) {
      Map<String, String> previousErrors = Map.copyOf(validationErrors);
      int previousRuntimes = packs.size();
      changed = false;
      validationErrors.clear();
      validationErrors.putAll(prepared.errors());
      for (String oldId : new ArrayList<>(packs.keySet())) {
        PluginApiPackRuntime oldRuntime = packs.get(oldId);
        PluginApiPackDefinition replacement = prepared.definitions().get(oldId);
        if (replacement != null) {
          if (oldRuntime.definition().rawContent().equals(replacement.rawContent())
              && oldRuntime.definition().sourcePath().equals(replacement.sourcePath())) {
            continue;
          }
          changed = true;
          oldRuntime.retire(sampleController);
          PluginApiPackRuntime newRuntime = new PluginApiPackRuntime(replacement, host);
          if (newRuntime.activate(sampleController)) {
            packs.put(oldId, newRuntime);
            collectFailures.remove(oldId);
          } else {
            oldRuntime.activate(sampleController);
            validationErrors.put(replacement.sourcePath().getFileName().toString(), newRuntime.detail());
          }
          continue;
        }
        Path oldPath = oldRuntime.definition().sourcePath().toAbsolutePath().normalize();
        if (!prepared.presentPaths().contains(oldPath)) {
          changed = true;
          oldRuntime.retire(sampleController);
          packs.remove(oldId);
          collectFailures.remove(oldId);
        }
      }
      for (PluginApiPackDefinition definition : prepared.definitions().values()) {
        if (packs.containsKey(definition.id())) {
          continue;
        }
        changed = true;
        PluginApiPackRuntime runtime = new PluginApiPackRuntime(definition, host);
        if (runtime.activate(sampleController)) {
          packs.put(definition.id(), runtime);
        } else {
          validationErrors.put(definition.sourcePath().getFileName().toString(), runtime.detail());
        }
      }
      changed = changed || previousRuntimes != packs.size() || !previousErrors.equals(validationErrors);
    }
    if (changed) {
      WebRevisions.bump(WebRevisions.Kind.PACKS);
    }
  }

  PreparedCatalog scan() throws IOException {
    return prepareCatalog();
  }

  private void installBundledExamples() throws IOException {
    if (Files.exists(packFolder)) {
      return;
    }
    Files.createDirectories(packFolder);
    for (String example : host.bundledExamples()) {
      Path target = packFolder.resolve(example);
      if (Files.exists(target)) {
        continue;
      }
      try (InputStream input = host.bundledExample(example)) {
        if (input != null) {
          Files.copy(input, target);
        }
      }
    }
  }

  private Path resolvePackPath(String id) throws IOException {
    Path resolved = packFolder.resolve(id + ".toml").toAbsolutePath().normalize();
    if (!resolved.startsWith(packFolder)) {
      throw new IOException("Pack path escapes the canonical folder");
    }
    return resolved;
  }

  private Path existingPackPath(String id) throws IOException {
    synchronized (packs) {
      PluginApiPackRuntime runtime = packs.get(id);
      if (runtime == null) {
        return resolvePackPath(id);
      }
      Path existing = runtime.definition().sourcePath().toAbsolutePath().normalize();
      if (!existing.startsWith(packFolder)) {
        throw new IOException("Pack source is outside the canonical folder");
      }
      return existing;
    }
  }

  private static SampleController sampleController() {
    return SamplerHosts.sampleController();
  }

  private static String normalizeId(String id) {
    return id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
  }

  record PreparedCatalog(
      Map<String, PluginApiPackDefinition> definitions,
      Map<String, String> errors,
      Set<Path> presentPaths
  ) {
  }

  public record ValidationResult(boolean valid, String id, int metricCount, String message) {
  }
}
