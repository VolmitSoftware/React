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
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.react.util.project.config.ConfigHotloadSnapshot;
import art.arcane.react.util.project.registry.Registered;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private volatile boolean enabled = true;

  @ConfigDoc(value = "Filesystem event polling interval in milliseconds.", impact = "Lower values detect delivered events sooner; automatic apply batches still run at most once every three seconds.")
  private volatile int pollIntervalMs = 500;

  @ConfigDoc(value = "Maximum number of key-level diff messages sent per changed file.", impact = "Lower values reduce operator chat noise on large config edits.")
  private volatile int maxDiffMessagesPerFile = 12;

  @ConfigDoc(value = "Sends hotload change summaries to online operators.", impact = "Disable if you only want console logs and no in-game notifications.")
  private volatile boolean notifyOperators = true;

  private transient File dataFolder;
  private transient File configToml;
  private transient File configLegacyJson;
  private transient File localeOverrideFolder;
  private transient volatile HotloadRuntime hotloadRuntime;
  private transient volatile String lastSlowTickPollSummary = "poll=not-run";

  public HotloadController() {
    super("react", "hotload", 500);
  }

  @Override
  public String getName() {
    return "Hotload";
  }

  @Override
  public void start() {
    stopWorker();
    dataFolder = React.instance.getDataFolder();
    configToml = React.instance.getDataFile("config.toml");
    configLegacyJson = React.instance.getDataFile("config.json");
    localeOverrideFolder = ReactLanguage.overrideFolder();
    HotloadTaskExecutor worker = new HotloadTaskExecutor(
        "React-Hotload-IO",
        failure -> logTransientFailure("React hotload IO worker failed", failure)
    );
    HotloadRuntime runtime = new HotloadRuntime(
        createHotloadEngine(),
        createPendingQueue(),
        new HotloadRevisionTracker(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentLinkedQueue<>(),
        new AtomicBoolean(),
        worker
    );
    hotloadRuntime = runtime;
    ConfigFileSupport.setSelfWriteListener(this::noteSelfWrite);
    worker.execute(() -> initializeAsync(runtime));
  }

  @Override
  public void stop() {
    ConfigFileSupport.setSelfWriteListener(null);
    stopWorker();
    lastSlowTickPollSummary = "poll=stopped";
  }

  @Override
  public void postStart() {

  }

  private ConfigHotloadEngine createHotloadEngine() {
    return new ConfigHotloadEngine(
        this::isManagedConfigFile,
        this::listKnownConfigFiles,
        this::readFileContent,
        this::normalizeContent
    );
  }

  private HotloadPendingQueue createPendingQueue() {
    return new HotloadPendingQueue(
        AUTOMATIC_HOTLOAD_COOLDOWN_NANOS,
        TOMBSTONE_GRACE_NANOS,
        System::nanoTime
    );
  }

  private void initializeAsync(HotloadRuntime runtime) {
    if (!isCurrentRuntime(runtime)) {
      return;
    }
    primeAppliedContents(runtime);
    if (!isCurrentRuntime(runtime)) {
      return;
    }
    reconfigureWatcher(runtime);
    if (!isCurrentRuntime(runtime)) {
      return;
    }
    queueDiskDivergences(runtime);
    lastSlowTickPollSummary = "poll=initialized async=true";
  }

  private void stopWorker() {
    HotloadRuntime runtime = hotloadRuntime;
    hotloadRuntime = null;
    if (runtime != null) {
      runtime.executor().retire(() -> {
        runtime.engine().clear();
        runtime.queue().clear();
        runtime.revisions().clear();
        runtime.selfWriteNotices().clear();
      });
    }
  }

  private boolean isCurrentRuntime(HotloadRuntime runtime) {
    return runtime != null && runtime == hotloadRuntime && !runtime.executor().isClosed();
  }

  @Override
  public void onTick() {
    HotloadRuntime runtime = hotloadRuntime;
    if (!isCurrentRuntime(runtime)) {
      lastSlowTickPollSummary = "poll=stopped";
      return;
    }
    if (runtime.executor().requestPoll(() -> pollConfigChanges(runtime))) {
      lastSlowTickPollSummary = "poll=queued async=true";
    }
  }

  private void reconfigureWatcher(HotloadRuntime runtime) {
    long effectivePollInterval = Math.max(100, pollIntervalMs);
    setTinterval(effectivePollInterval);
    if (!enabled) {
      runtime.engine().clear();
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

    runtime.engine().configure(
        effectivePollInterval,
        AUTOMATIC_HOTLOAD_COOLDOWN_MS,
        watchedFiles,
        watchedDirectories
    );
    React.info("Config hotload watcher enabled for config.toml and managed component configs.");
  }

  public void refreshAfterConfigReload() {
    HotloadRuntime runtime = hotloadRuntime;
    if (runtime != null) {
      runtime.executor().execute(() -> {
        if (isCurrentRuntime(runtime)) {
          reconfigureWatcherPreservingQueue(runtime);
        }
      });
    }
  }

  public String describeLastPollForSlowTick() {
    String summary = lastSlowTickPollSummary;
    if (summary == null || summary.isBlank()) {
      return "poll=unavailable";
    }

    return summary;
  }

  private void pollConfigChanges(HotloadRuntime runtime) {
    if (!isCurrentRuntime(runtime)) {
      return;
    }
    if (!enabled) {
      lastSlowTickPollSummary = "poll=disabled";
      return;
    }

    long pollStartNs = System.nanoTime();
    drainSelfWriteNotices(runtime);
    Set<File> touched = runtime.engine().pollTouchedFiles();
    if (!isCurrentRuntime(runtime)) {
      return;
    }
    enqueueTouchedFiles(runtime, touched);
    List<HotloadPendingQueue.ReadyChange> readyChanges = runtime.queue().beginDrain();
    if (readyChanges.isEmpty()) {
      long pollMs = (System.nanoTime() - pollStartNs) / 1_000_000L;
      lastSlowTickPollSummary = "ioPoll=" + pollMs + "ms async=true touched=" + touched.size()
          + " queued=" + runtime.queue().pendingCount()
          + " apply=none";
      return;
    }

    try {
      List<HotloadPendingQueue.ReadyChange> orderedTouched = new ArrayList<>(readyChanges);
      orderedTouched.sort(Comparator.comparing(change -> diagnosticRelativePath(change.path().toFile())));
      List<PreparedChange> prepared = new ArrayList<>(orderedTouched.size());
      List<String> touchedPreview = new ArrayList<>();
      PreparationContext preparationContext = new PreparationContext(ReactConfiguration.get().getLanguage());
      for (HotloadPendingQueue.ReadyChange change : orderedTouched) {
        if (!isCurrentRuntime(runtime)) {
          return;
        }
        File file = change.path().toFile();
        addPreviewEntry(touchedPreview, diagnosticRelativePath(file), 5);
        PreparedChange candidate = prepareQueuedChange(runtime, preparationContext, file, change.present());
        if (candidate != null) {
          prepared.add(candidate);
        }
      }
      if (!isCurrentRuntime(runtime)) {
        return;
      }
      if (prepared.isEmpty()) {
        finishPreparedBatch(runtime, orderedTouched.size(), touchedPreview, List.of(), pollStartNs);
        return;
      }

      lastSlowTickPollSummary = "ioPoll=" + ((System.nanoTime() - pollStartNs) / 1_000_000L)
          + "ms async=true touched=" + orderedTouched.size()
          + " prepared=" + prepared.size()
          + " apply=queued files=" + formatPreview(touchedPreview, orderedTouched.size());
      boolean scheduled;
      try {
        scheduled = FoliaScheduler.runGlobal(
            React.instance,
            () -> applyPreparedBatch(runtime, orderedTouched.size(), touchedPreview, prepared, pollStartNs)
        );
      } catch (Throwable failure) {
        logTransientFailure("Could not schedule the prepared hotload batch on the server thread", failure);
        scheduled = false;
      }
      if (!scheduled) {
        for (PreparedChange change : prepared) {
          enqueueChange(runtime, change.snapshot().path(), Files.isRegularFile(change.snapshot().path()));
        }
        finishPreparedBatch(runtime, orderedTouched.size(), touchedPreview, List.of(), pollStartNs);
      }
    } catch (Throwable failure) {
      for (HotloadPendingQueue.ReadyChange change : readyChanges) {
        enqueueChange(runtime, change.path(), Files.isRegularFile(change.path()));
      }
      runtime.queue().finishDrain();
      logTransientFailure("Could not prepare the React hotload batch; retained it for retry", failure);
    }
  }

  private void enqueueTouchedFiles(HotloadRuntime runtime, Set<File> touchedFiles) {
    if (touchedFiles == null || touchedFiles.isEmpty()) {
      return;
    }
    for (File file : touchedFiles) {
      if (file == null || isTemporaryArtifact(file)) {
        continue;
      }
      enqueueChange(runtime, file.toPath(), Files.isRegularFile(file.toPath()));
    }
  }

  private void enqueueChange(HotloadRuntime runtime, Path path, boolean present) {
    runtime.revisions().touch(path);
    runtime.queue().enqueue(path, present);
  }

  private PreparedChange prepareQueuedChange(
      HotloadRuntime runtime,
      PreparationContext preparationContext,
      File file,
      boolean expectedPresent
  ) {
    Path path = file.toPath().toAbsolutePath().normalize();
    long preparedRevision = runtime.revisions().current(path);
    if (!Files.isRegularFile(path)) {
      if (expectedPresent) {
        enqueueChange(runtime, path, false);
        return null;
      }
      React.warn("Ignored config deletion after the hotload grace window; last-good state remains active: "
          + diagnosticRelativePath(file));
      if (!runtime.revisions().runIfCurrent(
          path,
          preparedRevision,
          () -> acknowledgeMissing(runtime, file)
      )) {
        enqueueChange(runtime, path, Files.isRegularFile(path));
      }
      return null;
    }

    ConfigHotloadSnapshot snapshot;
    try {
      snapshot = ConfigHotloadSnapshot.capture(path, MAX_HOTLOAD_FILE_BYTES);
    } catch (NoSuchFileException e) {
      enqueueChange(runtime, path, false);
      return null;
    } catch (IOException e) {
      logTransientFailure("Could not capture a stable hotload snapshot for " + diagnosticRelativePath(file), e);
      enqueueChange(runtime, path, Files.isRegularFile(path));
      return null;
    }
    if (!isCurrentRuntime(runtime)) {
      return null;
    }

    String pathKey = normalizedPath(file);
    String before = runtime.appliedContents().get(pathKey);
    if (Objects.equals(before, snapshot.normalizedContent())) {
      if (!acknowledgeIfCurrent(runtime, snapshot, preparedRevision)) {
        enqueueChange(runtime, path, Files.isRegularFile(path));
      }
      requeueIfChanged(runtime, snapshot);
      return null;
    }

    PreparedApply preparedApply;
    try {
      preparedApply = prepareConfigSnapshot(preparationContext, file, snapshot.rawContent());
    } catch (Throwable failure) {
      React.warn("Skipped hotload for " + diagnosticRelativePath(file) + " due to invalid config: "
          + failure.getMessage());
      if (!acknowledgeIfCurrent(runtime, snapshot, preparedRevision)) {
        enqueueChange(runtime, path, Files.isRegularFile(path));
      }
      requeueIfChanged(runtime, snapshot);
      return null;
    }
    if (!isCurrentRuntime(runtime)) {
      return null;
    }
    List<Component> notifications;
    try {
      notifications = prepareOperatorNotifications(file, before, snapshot.rawContent());
    } catch (Throwable failure) {
      logTransientFailure("Could not prepare operator notifications for " + diagnosticRelativePath(file), failure);
      notifications = List.of();
    }
    return new PreparedChange(file, snapshot, preparedRevision, preparedApply, notifications);
  }

  private void applyPreparedBatch(
      HotloadRuntime runtime,
      int touchedCount,
      List<String> touchedPreview,
      List<PreparedChange> prepared,
      long pollStartNs
  ) {
    if (!isCurrentRuntime(runtime)) {
      return;
    }
    List<AppliedChange> results = new ArrayList<>(prepared.size());
    for (PreparedChange change : prepared) {
      ApplyOutcome outcome;
      try {
        HotloadRevisionTracker.GuardedBoolean guarded = runtime.revisions().runBooleanIfCurrent(
            change.snapshot().path(),
            change.revision(),
            change.apply()::apply
        );
        if (!guarded.current()) {
          outcome = ApplyOutcome.STALE;
        } else {
          outcome = guarded.value() ? ApplyOutcome.APPLIED : ApplyOutcome.REJECTED;
        }
      } catch (Throwable failure) {
        logTransientFailure("Hotload apply failed for " + diagnosticRelativePath(change.file()), failure);
        outcome = ApplyOutcome.RETRY;
      }
      if (outcome != ApplyOutcome.RETRY
          && !runtime.revisions().isCurrent(change.snapshot().path(), change.revision())) {
        outcome = ApplyOutcome.STALE;
      }
      if (outcome == ApplyOutcome.APPLIED) {
        try {
          deliverOperatorNotifications(change.notifications());
        } catch (Throwable failure) {
          logTransientFailure("Could not notify operators about the applied hotload for "
              + diagnosticRelativePath(change.file()), failure);
        }
      }
      results.add(new AppliedChange(change, outcome));
    }
    if (isCurrentRuntime(runtime)) {
      runtime.executor().execute(() -> finishPreparedBatch(
          runtime,
          touchedCount,
          touchedPreview,
          results,
          pollStartNs
      ));
    }
  }

  private void finishPreparedBatch(
      HotloadRuntime runtime,
      int touchedCount,
      List<String> touchedPreview,
      List<AppliedChange> results,
      long pollStartNs
  ) {
    if (!isCurrentRuntime(runtime)) {
      return;
    }
    int appliedCount = 0;
    List<String> appliedPreview = new ArrayList<>();
    try {
      for (AppliedChange result : results) {
        PreparedChange change = result.change();
        ConfigHotloadSnapshot snapshot = change.snapshot();
        if (result.outcome() == ApplyOutcome.APPLIED) {
          boolean current = runtime.revisions().runIfCurrent(
              snapshot.path(),
              change.revision(),
              () -> {
                runtime.appliedContents().put(normalizedPath(change.file()), snapshot.normalizedContent());
                acknowledgeSnapshot(runtime, snapshot);
              }
          );
          if (current) {
            appliedCount++;
            addPreviewEntry(appliedPreview, diagnosticRelativePath(change.file()), 5);
          } else {
            enqueueChange(runtime, snapshot.path(), Files.isRegularFile(snapshot.path()));
          }
        } else if (result.outcome() == ApplyOutcome.REJECTED) {
          if (!acknowledgeIfCurrent(runtime, snapshot, change.revision())) {
            enqueueChange(runtime, snapshot.path(), Files.isRegularFile(snapshot.path()));
          }
        } else {
          enqueueChange(runtime, snapshot.path(), Files.isRegularFile(snapshot.path()));
        }
        requeueIfChanged(runtime, snapshot);
      }
      applyDeferredWatcherReconfiguration(runtime);
    } finally {
      runtime.queue().finishDrain();
    }
    int skippedCount = Math.max(0, touchedCount - appliedCount);
    String summary = "cycle=" + ((System.nanoTime() - pollStartNs) / 1_000_000L)
        + "ms async=true touched=" + touchedCount
        + " queued=" + runtime.queue().pendingCount()
        + " applied=" + appliedCount
        + " skipped=" + skippedCount
        + " files=" + formatPreview(touchedPreview, touchedCount);
    if (appliedCount > 0) {
      summary += " appliedFiles=" + formatPreview(appliedPreview, appliedCount);
    }
    lastSlowTickPollSummary = summary;
  }

  private void requeueIfChanged(HotloadRuntime runtime, ConfigHotloadSnapshot appliedSnapshot) {
    Path path = appliedSnapshot.path();
    try {
      ConfigHotloadSnapshot latest = ConfigHotloadSnapshot.capture(path, MAX_HOTLOAD_FILE_BYTES);
      if (!latest.digest().equals(appliedSnapshot.digest())) {
        enqueueChange(runtime, path, true);
      }
    } catch (NoSuchFileException e) {
      enqueueChange(runtime, path, false);
    } catch (IOException e) {
      logTransientFailure("Could not verify the applied hotload snapshot for " + diagnosticRelativePath(path.toFile()), e);
      enqueueChange(runtime, path, Files.isRegularFile(path));
    }
  }

  private void applyDeferredWatcherReconfiguration(HotloadRuntime runtime) {
    if (!runtime.reconfigureAfterBatch().compareAndSet(true, false)) {
      return;
    }
    try {
      reconfigureWatcherPreservingQueue(runtime);
    } catch (Throwable e) {
      logTransientFailure("Could not reconfigure the React hotload watcher after applying its settings", e);
    }
  }

  private void reconfigureWatcherPreservingQueue(HotloadRuntime runtime) {
    queueDiskDivergences(runtime);
    try {
      reconfigureWatcher(runtime);
    } finally {
      queueDiskDivergences(runtime);
    }
  }

  private PreparedApply prepareConfigSnapshot(
      PreparationContext preparationContext,
      File file,
      String rawContent
  ) throws Exception {
    if (isShadowedLegacyJson(file)) {
      React.verbose("Ignoring legacy json hotload because canonical toml exists: " + file.getPath());
      return () -> false;
    }

    if (isMainConfigFile(file)) {
      ReactConfiguration preparedConfig = ReactConfiguration.prepareHotloadSnapshot(file, rawContent);
      ReactLanguage.PreparedReload preparedLanguage = ReactLanguage.prepareHotload(
          null,
          null,
          preparedConfig.getLanguage()
      );
      preparationContext.setConfiguredLocale(preparedConfig.getLanguage());
      return () -> {
        ReactConfiguration.applyHotloadSnapshot(preparedConfig);
        ReactLanguage.applyPreparedHotload(preparedLanguage);
        refreshGlobalRuntimeSettings();
        return true;
      };
    }

    if (ReactLanguage.isOverrideFile(file)) {
      ReactLanguage.PreparedReload preparedLanguage = ReactLanguage.prepareHotload(
          file,
          rawContent,
          preparationContext.configuredLocale()
      );
      return () -> ReactLanguage.applyPreparedHotload(preparedLanguage);
    }

    ManagedConfig target = resolveManagedConfig(file);
    if (target != null) {
      return switch (target.category) {
        case "core" -> prepareCoreConfig(target.id, file, rawContent);
        case "feature" -> prepareFeatureConfig(target.id, file, rawContent);
        case "tweak" -> prepareTweakConfig(target.id, file, rawContent);
        case "action" -> prepareActionConfig(target.id, file, rawContent);
        case "sampler" -> prepareSamplerConfig(target.id, file, rawContent);
        default -> validatedNoOp(file, rawContent);
      };
    }

    return validatedNoOp(file, rawContent);
  }

  private PreparedApply prepareCoreConfig(String id, File file, String rawContent) throws IOException {
    if (id == null || React.instance.getControllerRegistry() == null) {
      return validatedNoOp(file, rawContent);
    }

    IController controller = React.instance.getControllerRegistry().get(id);
    if (controller == null) {
      return validatedNoOp(file, rawContent);
    }
    Object prepared = controller.prepareConfigurationSnapshot(file, rawContent);
    return () -> {
      if (!controller.applyConfigurationSnapshot(prepared)) {
        return false;
      }
      if (controller == this) {
        HotloadRuntime runtime = hotloadRuntime;
        if (runtime != null) {
          runtime.reconfigureAfterBatch().set(true);
        }
      }
      if (controller instanceof PlayerController playerController) {
        playerController.updateMonitors();
      }
      return true;
    };
  }

  private PreparedApply prepareFeatureConfig(String id, File file, String rawContent) throws IOException {
    FeatureController controller = React.controller(FeatureController.class);
    if (controller == null || controller.getFeatures() == null || id == null) {
      return validatedNoOp(file, rawContent);
    }

    Feature feature = controller.getFeatures().get(id);
    if (feature == null) {
      return validatedNoOp(file, rawContent);
    }
    Object prepared = feature.prepareConfigurationSnapshot(file, rawContent);
    return () -> {
      boolean wasActive = controller.getActiveFeatures().containsKey(feature.getId());
      if (!feature.applyConfigurationSnapshot(prepared)) {
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
    };
  }

  private PreparedApply prepareTweakConfig(String id, File file, String rawContent) throws IOException {
    TweakController controller = React.controller(TweakController.class);
    if (controller == null || controller.getTweaks() == null || id == null) {
      return validatedNoOp(file, rawContent);
    }

    Tweak tweak = controller.getTweaks().get(id);
    if (tweak == null) {
      return validatedNoOp(file, rawContent);
    }
    Object prepared = tweak.prepareConfigurationSnapshot(file, rawContent);
    return () -> {
      boolean wasActive = controller.getActiveTweaks().containsKey(tweak.getId());
      if (!tweak.applyConfigurationSnapshot(prepared)) {
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
    };
  }

  private PreparedApply prepareActionConfig(String id, File file, String rawContent) throws IOException {
    ActionController controller = React.controller(ActionController.class);
    if (controller == null || controller.getActions() == null || id == null) {
      return validatedNoOp(file, rawContent);
    }

    Action<?> action = controller.getActions().get(id);
    if (action == null) {
      return validatedNoOp(file, rawContent);
    }
    Object prepared = action.prepareConfigurationSnapshot(file, rawContent);
    return () -> {
      boolean wasEnabled = action.isEnabled();
      if (!action.applyConfigurationSnapshot(prepared)) {
        return false;
      }
      if (!wasEnabled && action.isEnabled()) {
        action.onInit();
      }
      return true;
    };
  }

  private PreparedApply prepareSamplerConfig(String id, File file, String rawContent) throws IOException {
    SampleController controller = React.controller(SampleController.class);
    if (controller == null || controller.getSamplers() == null || id == null) {
      return validatedNoOp(file, rawContent);
    }

    Registered sampler = controller.getSamplers().get(id);
    if (sampler == null) {
      return validatedNoOp(file, rawContent);
    }
    Object prepared = sampler.prepareConfigurationSnapshot(file, rawContent);
    return () -> {
      boolean ok = controller.applySamplerConfig(id, prepared);
      if (!ok) {
        return false;
      }

      PlayerController playerController = React.controller(PlayerController.class);
      if (playerController != null) {
        playerController.updateMonitors();
      }

      return true;
    };
  }

  private PreparedApply validatedNoOp(File file, String rawContent) throws IOException {
    if (!validateConfigSnapshot(file, rawContent)) {
      throw new IOException("Config snapshot could not be parsed");
    }
    return () -> true;
  }

  private void refreshGlobalRuntimeSettings() {
    EntityController entityController = React.controller(EntityController.class);
    if (entityController != null) {
      ReactConfiguration.get().getPriority().rebuildPriority();
    }

    PlayerController playerController = React.controller(PlayerController.class);
    if (playerController != null) {
      playerController.updateMonitors();
    }
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

  private void primeAppliedContents(HotloadRuntime runtime) {
    for (File file : listKnownConfigFiles()) {
      if (!isCurrentRuntime(runtime)) {
        return;
      }
      if (file == null) {
        continue;
      }
      if (!Files.isRegularFile(file.toPath())) {
        runtime.observedDigests().putIfAbsent(normalizedPath(file), MISSING_DIGEST);
        continue;
      }
      try {
        ConfigHotloadSnapshot snapshot = ConfigHotloadSnapshot.capture(file.toPath(), MAX_HOTLOAD_FILE_BYTES);
        runtime.appliedContents().putIfAbsent(normalizedPath(file), snapshot.normalizedContent());
        runtime.observedDigests().putIfAbsent(normalizedPath(file), snapshot.digest());
      } catch (IOException e) {
        logTransientFailure("Could not establish the hotload baseline for " + diagnosticRelativePath(file), e);
      }
    }
  }

  private void noteSelfWrite(File file, String rawContent) {
    if (file == null || rawContent == null || !isManagedConfigFile(file)) {
      return;
    }
    HotloadRuntime runtime = hotloadRuntime;
    if (!isCurrentRuntime(runtime)) {
      return;
    }
    runtime.revisions().touchAndRun(
        file.toPath(),
        () -> runtime.appliedContents().put(normalizedPath(file), normalizeContent(rawContent))
    );
    runtime.selfWriteNotices().add(new SelfWriteNotice(file, rawContent));
    runtime.executor().requestPoll(() -> pollConfigChanges(runtime));
  }

  private void drainSelfWriteNotices(HotloadRuntime runtime) {
    SelfWriteNotice notice;
    while ((notice = runtime.selfWriteNotices().poll()) != null) {
      recordSelfWrite(runtime, notice.file(), notice.rawContent());
    }
  }

  private void recordSelfWrite(
      HotloadRuntime runtime,
      File file,
      String rawContent
  ) {
    runtime.engine().noteSelfWrite(file, rawContent);
    try {
      ConfigHotloadSnapshot snapshot = ConfigHotloadSnapshot.capture(file.toPath(), MAX_HOTLOAD_FILE_BYTES);
      if (Objects.equals(snapshot.normalizedContent(), normalizeContent(rawContent))) {
        runtime.observedDigests().put(normalizedPath(file), snapshot.digest());
      } else {
        enqueueChange(runtime, snapshot.path(), true);
      }
    } catch (IOException e) {
      logTransientFailure("Could not record the completed config write for " + diagnosticRelativePath(file), e);
      enqueueChange(runtime, file.toPath(), Files.isRegularFile(file.toPath()));
    }
  }

  private void acknowledgeSnapshot(HotloadRuntime runtime, ConfigHotloadSnapshot snapshot) {
    File file = snapshot.path().toFile();
    runtime.observedDigests().put(normalizedPath(file), snapshot.digest());
    runtime.engine().noteSelfWrite(file, snapshot.rawContent());
  }

  private boolean acknowledgeIfCurrent(
      HotloadRuntime runtime,
      ConfigHotloadSnapshot snapshot,
      long revision
  ) {
    return runtime.revisions().runIfCurrent(
        snapshot.path(),
        revision,
        () -> acknowledgeSnapshot(runtime, snapshot)
    );
  }

  private void acknowledgeMissing(HotloadRuntime runtime, File file) {
    runtime.observedDigests().put(normalizedPath(file), MISSING_DIGEST);
    runtime.engine().noteSelfWrite(file, null);
  }

  private void queueDiskDivergences(HotloadRuntime runtime) {
    Set<String> seen = new HashSet<>();
    for (File file : listKnownConfigFiles()) {
      if (!isCurrentRuntime(runtime)) {
        return;
      }
      if (file == null || !isManagedConfigFile(file)) {
        continue;
      }
      String pathKey = normalizedPath(file);
      seen.add(pathKey);
      queueDiskDivergence(runtime, file, pathKey);
    }

    for (String pathKey : new HashSet<>(runtime.observedDigests().keySet())) {
      if (seen.contains(pathKey)) {
        continue;
      }
      File file = new File(pathKey);
      if (isManagedConfigFile(file)) {
        queueDiskDivergence(runtime, file, pathKey);
      }
    }
  }

  private void queueDiskDivergence(HotloadRuntime runtime, File file, String pathKey) {
    Path path = file.toPath().toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) {
      if (!MISSING_DIGEST.equals(runtime.observedDigests().get(pathKey))) {
        enqueueChange(runtime, path, false);
      }
      return;
    }

    try {
      ConfigHotloadSnapshot snapshot = ConfigHotloadSnapshot.capture(path, MAX_HOTLOAD_FILE_BYTES);
      if (!snapshot.digest().equals(runtime.observedDigests().get(pathKey))) {
        enqueueChange(runtime, path, true);
      }
    } catch (NoSuchFileException e) {
      enqueueChange(runtime, path, false);
    } catch (IOException e) {
      logTransientFailure("Could not reconcile config state while reconfiguring the watcher for "
          + diagnosticRelativePath(file), e);
      enqueueChange(runtime, path, Files.isRegularFile(path));
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

  static String relativizeNormalized(File root, File file) {
    if (file == null) {
      return "<unknown>";
    }
    if (root == null) {
      return file.getName();
    }

    try {
      Path normalizedRoot = root.toPath().toAbsolutePath().normalize();
      Path normalizedFile = file.toPath().toAbsolutePath().normalize();
      return normalizedRoot.relativize(normalizedFile).toString();
    } catch (RuntimeException failure) {
      return file.getName();
    }
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

  private List<Component> prepareOperatorNotifications(File file, String before, String after) {
    if (!notifyOperators) {
      return List.of();
    }

    List<ConfigHotloadEngine.DiffEntry> diffs = ConfigHotloadEngine.computeStructuredDiff(
        before,
        after,
        raw -> parseStructured(raw, null)
    );
    if (diffs.isEmpty()) {
      return List.of();
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

    return List.copyOf(messages);
  }

  private void deliverOperatorNotifications(List<Component> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!player.isOp()) {
        continue;
      }

      // audience delivery: spigot Player has no sendMessage(Component)
      Audience audience = React.audiences().player(player);
      messages.forEach(audience::sendMessage);
    }
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
    File root = dataFolder;
    if (root == null && React.instance != null) {
      root = React.instance.getDataFolder();
    }
    return relativizeNormalized(root, file);
  }

  private enum ApplyOutcome {
    APPLIED,
    REJECTED,
    STALE,
    RETRY
  }

  @FunctionalInterface
  private interface PreparedApply {
    boolean apply();
  }

  private record PreparedChange(
      File file,
      ConfigHotloadSnapshot snapshot,
      long revision,
      PreparedApply apply,
      List<Component> notifications
  ) {
  }

  private record AppliedChange(PreparedChange change, ApplyOutcome outcome) {
  }

  private record SelfWriteNotice(File file, String rawContent) {
  }

  private record HotloadRuntime(
      ConfigHotloadEngine engine,
      HotloadPendingQueue queue,
      HotloadRevisionTracker revisions,
      Map<String, String> appliedContents,
      Map<String, String> observedDigests,
      ConcurrentLinkedQueue<SelfWriteNotice> selfWriteNotices,
      AtomicBoolean reconfigureAfterBatch,
      HotloadTaskExecutor executor
  ) {
  }

  private static final class PreparationContext {
    private String configuredLocale;

    private PreparationContext(String configuredLocale) {
      this.configuredLocale = configuredLocale;
    }

    private String configuredLocale() {
      return configuredLocale;
    }

    private void setConfiguredLocale(String configuredLocale) {
      this.configuredLocale = configuredLocale;
    }
  }

  private record ManagedConfig(String category, String id) {
  }
}
