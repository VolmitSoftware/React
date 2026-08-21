/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.react.util.project.config.ConfigHotloadSnapshot;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.localization.MessageArgument;
import com.google.gson.JsonElement;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

@ConfigDescription("Watches React config files and hot-applies changes without requiring a full /react reload.")
public class HotloadController extends TickedObject implements IController {
  private static final String[] MANAGED_CATEGORIES = {"core", "feature", "tweak", "action", "sampler"};
  private static final long AUTOMATIC_HOTLOAD_COOLDOWN_MS = 3_000L;
  private static final long AUTOMATIC_HOTLOAD_COOLDOWN_NANOS = TimeUnit.MILLISECONDS.toNanos(AUTOMATIC_HOTLOAD_COOLDOWN_MS);
  private static final long TOMBSTONE_GRACE_NANOS = TimeUnit.SECONDS.toNanos(3L);
  private static final long MAX_HOTLOAD_FILE_BYTES = 2L * 1024L * 1024L;
  private static final String MISSING_DIGEST = "<missing>";

  @ConfigDoc(value = "Enables live hotloading for React managed configs.", impact = "Set to false to disable file watching and require manual reloads.")
  private boolean enabled = true;

  @ConfigDoc(value = "Filesystem event polling interval in milliseconds.", impact = "Lower values detect delivered events sooner; automatic apply batches still run at most once every three seconds.")
  private int pollIntervalMs = 500;

  @ConfigDoc(value = "Maximum number of key-level diff messages sent per changed file.", impact = "Lower values reduce operator chat noise on large config edits.")
  private int maxDiffMessagesPerFile = 12;

  @ConfigDoc(value = "Sends hotload change summaries to online operators.", impact = "Disable if you only want console logs and no in-game notifications.")
  private boolean notifyOperators = true;

  private transient File dataFolder;
  private transient File configToml;
  private transient File configLegacyJson;
  private transient File localeOverrideFolder;
  private final transient ConfigHotloadEngine hotloadEngine = new ConfigHotloadEngine(
      this::isManagedConfigFile,
      this::listKnownConfigFiles,
      this::readFileContent,
      this::normalizeContent
  );
  private final transient HotloadPendingQueue pendingHotloads = new HotloadPendingQueue(
      AUTOMATIC_HOTLOAD_COOLDOWN_NANOS,
      TOMBSTONE_GRACE_NANOS,
      System::nanoTime
  );
  private final transient Map<String, String> lastAppliedContents = new ConcurrentHashMap<>();
  private final transient Map<String, String> lastObservedDigests = new ConcurrentHashMap<>();
  private transient volatile String lastSlowTickPollSummary = "poll=not-run";
  private transient volatile boolean reconfigureAfterBatch;

  public HotloadController() {
    super("react", "hotload", 500);
  }

  @Override
  public String getName() {
    return "Hotload";
  }

  @Override
  public void start() {
    pendingHotloads.clear();
    lastAppliedContents.clear();
    lastObservedDigests.clear();
    reconfigureAfterBatch = false;
    dataFolder = React.instance.getDataFolder();
    configToml = React.instance.getDataFile("config.toml");
    configLegacyJson = React.instance.getDataFile("config.json");
    localeOverrideFolder = ReactLanguage.overrideFolder();
    primeAppliedContents();
    reconfigureWatcher();
    queueDiskDivergences();
    ConfigFileSupport.setSelfWriteListener(this::noteSelfWrite);
  }

  @Override
  public void stop() {
    ConfigFileSupport.setSelfWriteListener(null);
    hotloadEngine.clear();
    pendingHotloads.clear();
    lastAppliedContents.clear();
    lastObservedDigests.clear();
    reconfigureAfterBatch = false;
    lastSlowTickPollSummary = "poll=stopped";
  }

  @Override
  public void postStart() {

  }

  @Override
  public void onTick() {
    pollConfigChanges();
  }

  private void reconfigureWatcher() {
    long effectivePollInterval = Math.max(100, pollIntervalMs);
    setTinterval(effectivePollInterval);
    if (!enabled) {
      hotloadEngine.clear();
      lastSlowTickPollSummary = "poll=disabled";
      return;
    }

    if (dataFolder == null) {
      dataFolder = React.instance.getDataFolder();
    }

    if (configToml == null) {
      configToml = React.instance.getDataFile("config.toml");
    }
    if (configLegacyJson == null) {
      configLegacyJson = React.instance.getDataFile("config.json");
    }

    List<File> watchedFiles = new ArrayList<>();
    watchedFiles.add(configToml);
    watchedFiles.add(configLegacyJson);
    List<File> watchedDirectories = new ArrayList<>();
    for (String category : MANAGED_CATEGORIES) {
      File categoryFolder = React.instance.getDataFolderNoCreate(category);
      watchedDirectories.add(categoryFolder);
    }
    watchedDirectories.add(localeOverrideFolder);

    hotloadEngine.configure(
        effectivePollInterval,
        AUTOMATIC_HOTLOAD_COOLDOWN_MS,
        watchedFiles,
        watchedDirectories
    );
    React.info("Config hotload watcher enabled for config.toml and managed component configs.");
  }

  public void refreshAfterConfigReload() {
    reconfigureWatcherPreservingQueue();
  }

  public String describeLastPollForSlowTick() {
    String summary = lastSlowTickPollSummary;
    if (summary == null || summary.isBlank()) {
      return "poll=unavailable";
    }

    return summary;
  }

  private void pollConfigChanges() {
    if (!enabled) {
      lastSlowTickPollSummary = "poll=disabled";
      return;
    }

    long pollStartNs = System.nanoTime();
    Set<File> touched = hotloadEngine.pollTouchedFiles();
    enqueueTouchedFiles(touched);
    List<HotloadPendingQueue.ReadyChange> readyChanges = pendingHotloads.beginDrain();
    if (readyChanges.isEmpty()) {
      long pollMs = (System.nanoTime() - pollStartNs) / 1_000_000L;
      lastSlowTickPollSummary = "poll=" + pollMs + "ms touched=" + touched.size()
          + " queued=" + pendingHotloads.pendingCount()
          + " applied=0 skipped=0 files=none";
      return;
    }

    List<HotloadPendingQueue.ReadyChange> orderedTouched = new ArrayList<>(readyChanges);
    orderedTouched.sort(Comparator.comparing(change -> diagnosticRelativePath(change.path().toFile())));
    List<String> touchedPreview = new ArrayList<>();
    List<String> appliedPreview = new ArrayList<>();
    int appliedCount = 0;
    long slowestApplyMs = 0L;
    String slowestFile = "";

    try {
      for (HotloadPendingQueue.ReadyChange change : orderedTouched) {
        File file = change.path().toFile();
        String relative = diagnosticRelativePath(file);
        addPreviewEntry(touchedPreview, relative, 5);
        long applyStartNs = System.nanoTime();
        QueuedApplyResult result = processQueuedChange(file, change.present());
        long applyMs = (System.nanoTime() - applyStartNs) / 1_000_000L;
        if (applyMs > slowestApplyMs) {
          slowestApplyMs = applyMs;
          slowestFile = relative;
        }

        if (result.applied()) {
          appliedCount++;
          addPreviewEntry(appliedPreview, relative, 5);
        }
      }
    } finally {
      try {
        applyDeferredWatcherReconfiguration();
      } finally {
        pendingHotloads.finishDrain();
      }
    }

    long pollMs = (System.nanoTime() - pollStartNs) / 1_000_000L;
    int touchedCount = orderedTouched.size();
    int skippedCount = Math.max(0, touchedCount - appliedCount);
    String summary = "poll=" + pollMs + "ms touched=" + touchedCount
        + " queued=" + pendingHotloads.pendingCount()
        + " applied=" + appliedCount
        + " skipped=" + skippedCount
        + " files=" + formatPreview(touchedPreview, touchedCount);
    if (appliedCount > 0) {
      summary += " appliedFiles=" + formatPreview(appliedPreview, appliedCount);
    }
    if (slowestApplyMs > 0L && !slowestFile.isBlank()) {
      summary += " slowest=" + slowestFile + ":" + slowestApplyMs + "ms";
    }

    lastSlowTickPollSummary = summary;
  }

  private void enqueueTouchedFiles(Set<File> touchedFiles) {
    if (touchedFiles == null || touchedFiles.isEmpty()) {
      return;
    }
    for (File file : touchedFiles) {
      if (file == null || isTemporaryArtifact(file)) {
        continue;
      }
      pendingHotloads.enqueue(file.toPath(), Files.isRegularFile(file.toPath()));
    }
  }

  private QueuedApplyResult processQueuedChange(File file, boolean expectedPresent) {
    Path path = file.toPath().toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) {
      if (expectedPresent) {
        pendingHotloads.enqueue(path, false);
        return QueuedApplyResult.SKIPPED;
      }
      React.warn("Ignored config deletion after the hotload grace window; last-good state remains active: "
          + diagnosticRelativePath(file));
      acknowledgeMissing(file);
      return QueuedApplyResult.SKIPPED;
    }

    ConfigHotloadSnapshot snapshot;
    try {
      snapshot = ConfigHotloadSnapshot.capture(path, MAX_HOTLOAD_FILE_BYTES);
    } catch (NoSuchFileException e) {
      pendingHotloads.enqueue(path, false);
      return QueuedApplyResult.SKIPPED;
    } catch (IOException e) {
      logTransientFailure("Could not capture a stable hotload snapshot for " + diagnosticRelativePath(file), e);
      pendingHotloads.enqueue(path, Files.isRegularFile(path));
      return QueuedApplyResult.SKIPPED;
    }

    String pathKey = normalizedPath(file);
    String before = lastAppliedContents.get(pathKey);
    if (Objects.equals(before, snapshot.normalizedContent())) {
      acknowledgeSnapshot(snapshot);
      requeueIfChanged(snapshot);
      return QueuedApplyResult.SKIPPED;
    }

    ApplyOutcome outcome = applyConfigSnapshot(file, snapshot.rawContent());
    if (outcome == ApplyOutcome.APPLIED) {
      lastAppliedContents.put(pathKey, snapshot.normalizedContent());
      acknowledgeSnapshot(snapshot);
      try {
        notifyOps(file, before, snapshot.rawContent());
      } catch (Throwable e) {
        logTransientFailure("Could not notify operators about the applied hotload for "
            + diagnosticRelativePath(file), e);
      }
    } else if (outcome == ApplyOutcome.REJECTED) {
      acknowledgeSnapshot(snapshot);
    } else if (outcome == ApplyOutcome.RETRY) {
      pendingHotloads.enqueue(path, Files.isRegularFile(path));
    }

    requeueIfChanged(snapshot);
    return outcome == ApplyOutcome.APPLIED ? QueuedApplyResult.APPLIED : QueuedApplyResult.SKIPPED;
  }

  private ApplyOutcome applyConfigSnapshot(File file, String rawContent) {
    try {
      boolean applied = applyConfigChange(file, rawContent);
      return applied ? ApplyOutcome.APPLIED : ApplyOutcome.REJECTED;
    } catch (Throwable e) {
      logTransientFailure("Hotload apply failed for " + diagnosticRelativePath(file), e);
      return ApplyOutcome.RETRY;
    }
  }

  private void requeueIfChanged(ConfigHotloadSnapshot appliedSnapshot) {
    Path path = appliedSnapshot.path();
    try {
      ConfigHotloadSnapshot latest = ConfigHotloadSnapshot.capture(path, MAX_HOTLOAD_FILE_BYTES);
      if (!latest.digest().equals(appliedSnapshot.digest())) {
        pendingHotloads.enqueue(path, true);
      }
    } catch (NoSuchFileException e) {
      pendingHotloads.enqueue(path, false);
    } catch (IOException e) {
      logTransientFailure("Could not verify the applied hotload snapshot for " + diagnosticRelativePath(path.toFile()), e);
      pendingHotloads.enqueue(path, Files.isRegularFile(path));
    }
  }

  private void applyDeferredWatcherReconfiguration() {
    if (!reconfigureAfterBatch) {
      return;
    }
    reconfigureAfterBatch = false;
    try {
      reconfigureWatcherPreservingQueue();
    } catch (Throwable e) {
      logTransientFailure("Could not reconfigure the React hotload watcher after applying its settings", e);
    }
  }

  private void reconfigureWatcherPreservingQueue() {
    queueDiskDivergences();
    try {
      reconfigureWatcher();
    } finally {
      queueDiskDivergences();
    }
  }

  private boolean applyConfigChange(File file, String rawContent) {
    if (isShadowedLegacyJson(file)) {
      React.verbose("Ignoring legacy json hotload because canonical toml exists: " + file.getPath());
      return false;
    }

    if (isMainConfigFile(file)) {
      boolean ok = ConfigFileSupport.withPassiveHotloadSnapshot(
          file,
          rawContent,
          ReactConfiguration::reload
      );
      if (ok) {
        refreshGlobalRuntimeSettings();
      } else {
        React.warn("Skipped hotload for " + file.getPath() + " due to invalid config.");
      }
      return ok;
    }

    if (ReactLanguage.isOverrideFile(file)) {
      return ReactLanguage.reload(file, rawContent);
    }

    ManagedConfig target = resolveManagedConfig(file);
    if (target != null) {
      return switch (target.category) {
        case "core" -> reloadCoreConfig(target.id, file, rawContent);
        case "feature" -> reloadFeatureConfig(target.id, file, rawContent);
        case "tweak" -> reloadTweakConfig(target.id, file, rawContent);
        case "action" -> reloadActionConfig(target.id, file, rawContent);
        case "sampler" -> reloadSamplerConfig(target.id, file, rawContent);
        default -> validateConfigSnapshot(file, rawContent);
      };
    }

    return validateConfigSnapshot(file, rawContent);
  }

  private boolean reloadCoreConfig(String id, File file, String rawContent) {
    if (id == null || React.instance.getControllerRegistry() == null) {
      return validateConfigSnapshot(file, rawContent);
    }

    IController controller = React.instance.getControllerRegistry().get(id);
    if (controller == null) {
      return validateConfigSnapshot(file, rawContent);
    }

    return runSync(() -> ConfigFileSupport.withPassiveHotloadSnapshot(file, rawContent, () -> {
      if (!controller.reloadConfiguration()) {
        return false;
      }
      if (controller == this) {
        reconfigureAfterBatch = true;
      }
      if (controller instanceof PlayerController playerController) {
        playerController.updateMonitors();
      }
      return true;
    }));
  }

  private boolean reloadFeatureConfig(String id, File file, String rawContent) {
    FeatureController controller = React.controller(FeatureController.class);
    if (controller == null || controller.getFeatures() == null || id == null) {
      return validateConfigSnapshot(file, rawContent);
    }

    Feature feature = controller.getFeatures().get(id);
    if (feature == null) {
      return validateConfigSnapshot(file, rawContent);
    }

    return runSync(() -> ConfigFileSupport.withPassiveHotloadSnapshot(file, rawContent, () -> {
      boolean wasActive = controller.getActiveFeatures().containsKey(feature.getId());
      if (!feature.reloadConfiguration()) {
        return false;
      }
      boolean nowEnabled = feature.isEnabled();

      if (wasActive && !nowEnabled) {
        controller.deactivateFeature(feature);
      } else if (!wasActive && nowEnabled) {
        controller.activateFeature(feature);
      } else if (wasActive) {
        controller.deactivateFeature(feature);
        controller.activateFeature(feature);
      }

      return true;
    }));
  }

  private boolean reloadTweakConfig(String id, File file, String rawContent) {
    TweakController controller = React.controller(TweakController.class);
    if (controller == null || controller.getTweaks() == null || id == null) {
      return validateConfigSnapshot(file, rawContent);
    }

    Tweak tweak = controller.getTweaks().get(id);
    if (tweak == null) {
      return validateConfigSnapshot(file, rawContent);
    }

    return runSync(() -> ConfigFileSupport.withPassiveHotloadSnapshot(file, rawContent, () -> {
      boolean wasActive = controller.getActiveTweaks().containsKey(tweak.getId());
      if (!tweak.reloadConfiguration()) {
        return false;
      }
      boolean nowEnabled = tweak.isEnabled();

      if (wasActive && !nowEnabled) {
        controller.deactivateTweak(tweak);
      } else if (!wasActive && nowEnabled) {
        controller.activateTweak(tweak);
      } else if (wasActive) {
        controller.deactivateTweak(tweak);
        controller.activateTweak(tweak);
      }

      return true;
    }));
  }

  private boolean reloadActionConfig(String id, File file, String rawContent) {
    ActionController controller = React.controller(ActionController.class);
    if (controller == null || controller.getActions() == null || id == null) {
      return validateConfigSnapshot(file, rawContent);
    }

    Action<?> action = controller.getActions().get(id);
    if (action == null) {
      return validateConfigSnapshot(file, rawContent);
    }

    return runSync(() -> ConfigFileSupport.withPassiveHotloadSnapshot(file, rawContent, () -> {
      boolean wasEnabled = action.isEnabled();
      if (!action.reloadConfiguration()) {
        return false;
      }
      if (!wasEnabled && action.isEnabled()) {
        action.onInit();
      }
      return true;
    }));
  }

  private boolean reloadSamplerConfig(String id, File file, String rawContent) {
    SampleController controller = React.controller(SampleController.class);
    if (controller == null || controller.getSamplers() == null || id == null) {
      return validateConfigSnapshot(file, rawContent);
    }

    if (controller.getSamplers().get(id) == null) {
      return validateConfigSnapshot(file, rawContent);
    }

    return runSync(() -> ConfigFileSupport.withPassiveHotloadSnapshot(file, rawContent, () -> {
      boolean ok = controller.reloadSamplerConfig(id);
      if (!ok) {
        return false;
      }

      PlayerController playerController = React.controller(PlayerController.class);
      if (playerController != null) {
        playerController.updateMonitors();
      }

      return true;
    }));
  }

  private boolean runSync(BooleanSupplier supplier) {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Boolean result = J.sResult(() -> {
      try {
        return supplier.getAsBoolean();
      } catch (Throwable e) {
        failure.set(e);
        return false;
      }
    });
    Throwable applyFailure = failure.get();
    if (applyFailure != null) {
      throw new IllegalStateException("Synchronous hotload application failed", applyFailure);
    }
    if (result == null) {
      throw new IllegalStateException("Synchronous hotload application was interrupted, timed out, or refused");
    }
    return Boolean.TRUE.equals(result);
  }

  private void refreshGlobalRuntimeSettings() {
    ReactLanguage.reload();
    J.s(() -> {
      EntityController entityController = React.controller(EntityController.class);
      if (entityController != null) {
        ReactConfiguration.get().getPriority().rebuildPriority();
      }

      PlayerController playerController = React.controller(PlayerController.class);
      if (playerController != null) {
        playerController.updateMonitors();
      }
    });
  }

  private boolean validateConfigSnapshot(File file, String rawContent) {
    if (file == null || rawContent == null || rawContent.isBlank()) {
      return false;
    }

    JsonElement parsed = parseStructured(rawContent, file);
    return parsed != null;
  }

  private ManagedConfig resolveManagedConfig(File file) {
    String relative = relativizeToDataFolder(file).replace('\\', '/');
    String[] parts = relative.split("/");
    if (parts.length != 2) {
      return null;
    }

    String category = parts[0].toLowerCase(Locale.ROOT);
    if (!isManagedCategory(category)) {
      return null;
    }

    String id = ConfigFileSupport.configNameFromFileName(parts[1]);
    if (id == null || id.isBlank()) {
      return null;
    }

    return new ManagedConfig(category, id);
  }

  private boolean isManagedCategory(String category) {
    if (category == null) {
      return false;
    }

    for (String managedCategory : MANAGED_CATEGORIES) {
      if (managedCategory.equals(category)) {
        return true;
      }
    }
    return false;
  }

  private boolean isMainConfigFile(File file) {
    return sameFile(file, configToml) || sameFile(file, configLegacyJson);
  }

  private boolean isShadowedLegacyJson(File file) {
    if (file == null || !file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
      return false;
    }

    if (sameFile(file, configLegacyJson) && configToml != null && configToml.exists()) {
      return true;
    }

    ManagedConfig managed = resolveManagedConfig(file);
    if (managed != null) {
      return ConfigFileSupport.toTomlFile(file).exists();
    }

    return false;
  }

  private boolean isManagedConfigFile(File file) {
    if (file == null || isTemporaryArtifact(file) || !ConfigFileSupport.isSupportedConfigFile(file)) {
      return false;
    }

    if (ReactLanguage.isOverrideFile(file)) {
      return true;
    }

    String relative = relativizeToDataFolder(file).replace('\\', '/').toLowerCase(Locale.ROOT);
    if ("config.toml".equals(relative) || "config.json".equals(relative)) {
      return true;
    }

    String[] parts = relative.split("/");
    if (parts.length != 2) {
      return false;
    }

    return isManagedCategory(parts[0]) && ConfigFileSupport.configNameFromFileName(parts[1]) != null;
  }

  static boolean isTemporaryArtifactName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return true;
    }
    String name = fileName.toLowerCase(Locale.ROOT);
    if (name.startsWith(".")
        || name.startsWith("~$")
        || name.endsWith("~")
        || (name.startsWith("#") && name.endsWith("#"))) {
      return true;
    }
    if (name.endsWith(".tmp")
        || name.endsWith(".temp")
        || name.endsWith(".part")
        || name.endsWith(".swp")
        || name.endsWith(".swo")
        || name.endsWith(".crdownload")
        || name.endsWith(".download")
        || name.endsWith(".filepart")
        || name.endsWith(".partial")
        || name.endsWith(".upload")
        || name.endsWith(".uploading")) {
      return true;
    }
    return name.contains(".tmp.")
        || name.contains(".temp.")
        || name.contains(".part.")
        || name.contains(".swp.")
        || name.contains(".swo.")
        || name.contains("___jb_tmp___")
        || name.contains("___jb_old___");
  }

  private boolean isTemporaryArtifact(File file) {
    return file == null || isTemporaryArtifactName(file.getName());
  }

  private boolean sameFile(File a, File b) {
    return a != null && b != null && a.getAbsoluteFile().equals(b.getAbsoluteFile());
  }

  private List<File> listKnownConfigFiles() {
    List<File> files = new ArrayList<>();
    Set<String> added = new HashSet<>();

    addIfConfig(files, added, configToml);
    addIfConfig(files, added, configLegacyJson);

    if (dataFolder == null) {
      dataFolder = React.instance == null ? null : React.instance.getDataFolder();
    }

    if (dataFolder == null || !dataFolder.exists() || !dataFolder.isDirectory()) {
      return files;
    }

    for (String category : MANAGED_CATEGORIES) {
      File categoryFolder = new File(dataFolder, category);
      File[] children = categoryFolder.listFiles();
      if (children == null || children.length == 0) {
        continue;
      }

      for (File child : children) {
        if (child == null || !child.isFile()) {
          continue;
        }

        addIfConfig(files, added, child);
      }
    }

    File[] localeFiles = localeOverrideFolder == null ? null : localeOverrideFolder.listFiles();
    if (localeFiles != null) {
      for (File localeFile : localeFiles) {
        addIfConfig(files, added, localeFile);
      }
    }

    return files;
  }

  private void primeAppliedContents() {
    for (File file : listKnownConfigFiles()) {
      if (file == null) {
        continue;
      }
      if (!Files.isRegularFile(file.toPath())) {
        lastObservedDigests.putIfAbsent(normalizedPath(file), MISSING_DIGEST);
        continue;
      }
      try {
        ConfigHotloadSnapshot snapshot = ConfigHotloadSnapshot.capture(file.toPath(), MAX_HOTLOAD_FILE_BYTES);
        lastAppliedContents.putIfAbsent(normalizedPath(file), snapshot.normalizedContent());
        lastObservedDigests.putIfAbsent(normalizedPath(file), snapshot.digest());
      } catch (IOException e) {
        logTransientFailure("Could not establish the hotload baseline for " + diagnosticRelativePath(file), e);
      }
    }
  }

  private void noteSelfWrite(File file, String rawContent) {
    hotloadEngine.noteSelfWrite(file, rawContent);
    if (file == null || rawContent == null || !isManagedConfigFile(file)) {
      return;
    }
    lastAppliedContents.put(normalizedPath(file), normalizeContent(rawContent));
    try {
      ConfigHotloadSnapshot snapshot = ConfigHotloadSnapshot.capture(file.toPath(), MAX_HOTLOAD_FILE_BYTES);
      if (Objects.equals(snapshot.normalizedContent(), normalizeContent(rawContent))) {
        lastObservedDigests.put(normalizedPath(file), snapshot.digest());
      } else {
        pendingHotloads.enqueue(snapshot.path(), true);
      }
    } catch (IOException e) {
      logTransientFailure("Could not record the completed config write for " + diagnosticRelativePath(file), e);
      pendingHotloads.enqueue(file.toPath(), Files.isRegularFile(file.toPath()));
    }
  }

  private void acknowledgeSnapshot(ConfigHotloadSnapshot snapshot) {
    File file = snapshot.path().toFile();
    lastObservedDigests.put(normalizedPath(file), snapshot.digest());
    hotloadEngine.noteSelfWrite(file, snapshot.rawContent());
  }

  private void acknowledgeMissing(File file) {
    lastObservedDigests.put(normalizedPath(file), MISSING_DIGEST);
    hotloadEngine.noteSelfWrite(file, null);
  }

  private void queueDiskDivergences() {
    Set<String> seen = new HashSet<>();
    for (File file : listKnownConfigFiles()) {
      if (file == null || !isManagedConfigFile(file)) {
        continue;
      }
      String pathKey = normalizedPath(file);
      seen.add(pathKey);
      queueDiskDivergence(file, pathKey);
    }

    for (String pathKey : new HashSet<>(lastObservedDigests.keySet())) {
      if (seen.contains(pathKey)) {
        continue;
      }
      File file = new File(pathKey);
      if (isManagedConfigFile(file)) {
        queueDiskDivergence(file, pathKey);
      }
    }
  }

  private void queueDiskDivergence(File file, String pathKey) {
    Path path = file.toPath().toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) {
      if (!MISSING_DIGEST.equals(lastObservedDigests.get(pathKey))) {
        pendingHotloads.enqueue(path, false);
      }
      return;
    }

    try {
      ConfigHotloadSnapshot snapshot = ConfigHotloadSnapshot.capture(path, MAX_HOTLOAD_FILE_BYTES);
      if (!snapshot.digest().equals(lastObservedDigests.get(pathKey))) {
        pendingHotloads.enqueue(path, true);
      }
    } catch (NoSuchFileException e) {
      pendingHotloads.enqueue(path, false);
    } catch (IOException e) {
      logTransientFailure("Could not reconcile config state while reconfiguring the watcher for "
          + diagnosticRelativePath(file), e);
      pendingHotloads.enqueue(path, Files.isRegularFile(path));
    }
  }

  private void addIfConfig(List<File> out, Set<String> added, File file) {
    if (!isManagedConfigFile(file)) {
      return;
    }

    String path = file.getAbsolutePath();
    if (!added.add(path)) {
      return;
    }

    out.add(file);
  }

  private String readFileContent(File file) {
    if (file == null || !file.exists() || !file.isFile()) {
      return null;
    }

    try (InputStream input = Files.newInputStream(file.toPath())) {
      byte[] content = input.readNBytes((int) MAX_HOTLOAD_FILE_BYTES + 1);
      if (content.length > MAX_HOTLOAD_FILE_BYTES) {
        throw new IOException("Config exceeds " + MAX_HOTLOAD_FILE_BYTES + " bytes: " + file);
      }
      return new String(content, StandardCharsets.UTF_8);
    } catch (Throwable e) {
      logTransientFailure("Could not read watched config " + diagnosticRelativePath(file), e);
      return null;
    }
  }

  private String normalizedPath(File file) {
    return file.toPath().toAbsolutePath().normalize().toString();
  }

  private void logTransientFailure(String message, Throwable failure) {
    if (React.instance != null) {
      React.instance.getLogger().log(Level.WARNING, message, failure);
      return;
    }
    System.err.println("[React] " + message);
    failure.printStackTrace();
  }

  private String normalizeContent(String text) {
    if (text == null) {
      return null;
    }
    return ConfigFileSupport.normalize(text);
  }

  private String diagnosticRelativePath(File file) {
    return relativizeToDataFolder(file).replace('\\', '/');
  }

  private void addPreviewEntry(List<String> preview, String value, int limit) {
    if (preview == null || value == null || value.isBlank()) {
      return;
    }

    if (preview.size() >= Math.max(1, limit)) {
      return;
    }

    preview.add(value);
  }

  private String formatPreview(List<String> preview, int total) {
    if (total <= 0) {
      return "none";
    }

    if (preview == null || preview.isEmpty()) {
      return "+" + total + " files";
    }

    int remaining = Math.max(0, total - preview.size());
    String base = String.join(", ", preview);
    if (remaining <= 0) {
      return base;
    }

    return base + ", +" + remaining + " more";
  }

  private JsonElement parseStructured(String raw, File file) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    return ConfigFileSupport.parseToJsonElement(raw, file);
  }

  private void notifyOps(File file, String before, String after) {
    if (!notifyOperators) {
      return;
    }

    List<ConfigHotloadEngine.DiffEntry> diffs = ConfigHotloadEngine.computeStructuredDiff(
        before,
        after,
        raw -> parseStructured(raw, null)
    );
    if (diffs.isEmpty()) {
      return;
    }

    String relative = relativizeToDataFolder(file);
    List<Component> messages = new ArrayList<>();
    int shown = Math.min(Math.max(1, maxDiffMessagesPerFile), diffs.size());
    for (int i = 0; i < shown; i++) {
      ConfigHotloadEngine.DiffEntry diff = diffs.get(i);
      messages.add(formatHotloadMessage(relative, diff.key(), diff.oldValue(), diff.newValue()));
    }

    if (diffs.size() > shown) {
      int remaining = diffs.size() - shown;
      messages.add(ReactLanguage.prefixedComponent(
          RuntimeMessages.HOTLOAD_TRUNCATED,
          MessageArgument.untrusted("count", remaining),
          MessageArgument.untrusted("file", relative)
      ));
    }

    J.s(() -> {
      for (Player player : Bukkit.getOnlinePlayers()) {
        if (!player.isOp()) {
          continue;
        }

        // audience delivery: spigot Player has no sendMessage(Component)
        Audience audience = React.audiences().player(player);
        messages.forEach(audience::sendMessage);
      }
    });
  }

  private Component formatHotloadMessage(String file, String key, String oldValue, String newValue) {
    return ReactLanguage.prefixedComponent(
        RuntimeMessages.HOTLOAD_DIFF,
        MessageArgument.untrusted("file", file),
        MessageArgument.untrusted("key", key),
        MessageArgument.untrusted("before", formatValue(oldValue)),
        MessageArgument.untrusted("after", formatValue(newValue))
    );
  }

  private String formatValue(String value) {
    return ConfigHotloadEngine.compactValue(value, 120);
  }

  private String relativizeToDataFolder(File file) {
    try {
      return React.instance.getDataFolder().toPath().relativize(file.toPath()).toString();
    } catch (Throwable e) {
      return file == null ? "<unknown>" : file.getName();
    }
  }

  private enum ApplyOutcome {
    APPLIED,
    REJECTED,
    RETRY
  }

  private record QueuedApplyResult(boolean applied) {
    private static final QueuedApplyResult APPLIED = new QueuedApplyResult(true);
    private static final QueuedApplyResult SKIPPED = new QueuedApplyResult(false);
  }

  private record ManagedConfig(String category, String id) {
  }
}
