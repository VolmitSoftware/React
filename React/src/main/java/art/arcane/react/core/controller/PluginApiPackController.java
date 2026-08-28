package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackParser;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime.PackStatus;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;

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

public final class PluginApiPackController extends TickedObject implements IController {
  public static final int MAX_PACKS = 32;
  public static final int MAX_TOTAL_METRICS = 256;
  private static final long SCAN_INTERVAL_MS = 3_000L;
  private static final List<String> EXAMPLES = List.of(
      "adapt-ability-ops.toml",
      "placeholderapi-server.toml",
      "oraxen-runtime.toml"
  );

  private final AtomicBoolean collectionQueued = new AtomicBoolean();
  private final AtomicBoolean running = new AtomicBoolean();
  private final AtomicBoolean scanQueued = new AtomicBoolean();
  private final Map<String, PluginApiPackRuntime> packs = new LinkedHashMap<>();
  private final Map<String, String> validationErrors = new LinkedHashMap<>();
  private transient ExecutorService ioExecutor;
  private transient Path packFolder;
  private transient long lastScanMs;

  public PluginApiPackController() {
    super("react", "plugin-api-packs", 1_000L);
  }

  @Override
  public String getName() {
    return "Plugin API Packs";
  }

  @Override
  public void start() {
    running.set(true);
    packFolder = React.instance.getDataFolder().toPath().resolve("plugin-apis").toAbsolutePath().normalize();
    ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "React-PluginApi-IO");
      thread.setDaemon(true);
      return thread;
    });
    lastScanMs = 0L;
  }

  @Override
  public void postStart() {
    requestReload();
  }

  @Override
  public void stop() {
    running.set(false);
    ExecutorService executor = ioExecutor;
    ioExecutor = null;
    if (executor != null) {
      executor.shutdownNow();
    }
    SampleController sampleController = React.controller(SampleController.class);
    synchronized (packs) {
      for (PluginApiPackRuntime runtime : packs.values()) {
        runtime.retire(sampleController);
      }
      packs.clear();
      validationErrors.clear();
    }
  }

  @Override
  public void onTick() {
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
        React.warn("Plugin API pack scan failed", failure);
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

  public ValidationResult validate(String rawContent) {
    try {
      PluginApiPackDefinition definition = PluginApiPackParser.parse(Path.of("validation.toml"), rawContent);
      return new ValidationResult(true, definition.id(), definition.metrics().size(), "valid");
    } catch (IOException failure) {
      return new ValidationResult(false, "", 0, failure.getMessage());
    }
  }

  public PackStatus install(String requestedId, String rawContent) throws IOException {
    PluginApiPackDefinition definition = PluginApiPackParser.parse(Path.of("upload.toml"), rawContent);
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
      }
      removed.retire(React.controller(SampleController.class));
      return true;
    });
    return true;
  }

  private void collectOnServerThread() {
    long now = System.currentTimeMillis();
    synchronized (packs) {
      for (PluginApiPackRuntime runtime : packs.values()) {
        runtime.collect(now);
      }
    }
  }

  private PreparedCatalog prepareCatalog() throws IOException {
    Files.createDirectories(packFolder);
    List<Path> files;
    try (java.util.stream.Stream<Path> stream = Files.list(packFolder)) {
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
        PluginApiPackDefinition definition = PluginApiPackParser.parse(normalized, raw);
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

  private void applyCatalog(PreparedCatalog prepared) {
    SampleController sampleController = React.controller(SampleController.class);
    synchronized (packs) {
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
          oldRuntime.retire(sampleController);
          PluginApiPackRuntime newRuntime = new PluginApiPackRuntime(replacement);
          if (newRuntime.activate(sampleController)) {
            packs.put(oldId, newRuntime);
          } else {
            oldRuntime.activate(sampleController);
            validationErrors.put(replacement.sourcePath().getFileName().toString(), newRuntime.detail());
          }
          continue;
        }
        Path oldPath = oldRuntime.definition().sourcePath().toAbsolutePath().normalize();
        if (!prepared.presentPaths().contains(oldPath)) {
          oldRuntime.retire(sampleController);
          packs.remove(oldId);
        }
      }
      for (PluginApiPackDefinition definition : prepared.definitions().values()) {
        if (packs.containsKey(definition.id())) {
          continue;
        }
        PluginApiPackRuntime runtime = new PluginApiPackRuntime(definition);
        if (runtime.activate(sampleController)) {
          packs.put(definition.id(), runtime);
        } else {
          validationErrors.put(definition.sourcePath().getFileName().toString(), runtime.detail());
        }
      }
    }
  }

  private void installBundledExamples() throws IOException {
    if (Files.exists(packFolder)) {
      return;
    }
    Files.createDirectories(packFolder);
    for (String example : EXAMPLES) {
      Path target = packFolder.resolve(example);
      if (Files.exists(target)) {
        continue;
      }
      try (InputStream input = React.instance.getResource("plugin-apis/examples/" + example)) {
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

  private String normalizeId(String id) {
    return id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
  }

  private record PreparedCatalog(
      Map<String, PluginApiPackDefinition> definitions,
      Map<String, String> errors,
      Set<Path> presentPaths
  ) {
  }

  public record ValidationResult(boolean valid, String id, int metricCount, String message) {
  }
}
