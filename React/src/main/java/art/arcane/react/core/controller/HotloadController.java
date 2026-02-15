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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.config.ConfigDescription;
import art.arcane.react.util.config.ConfigDoc;
import art.arcane.react.util.config.ConfigFileSupport;
import art.arcane.react.util.config.ConfigRewriteReporter;
import art.arcane.react.util.format.C;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.scheduling.J;
import art.arcane.react.util.scheduling.TickedObject;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.io.IO;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

@ConfigDescription("Watches React config files and hot-applies changes without requiring a full /react reload.")
public class HotloadController extends TickedObject implements IController {
    private static final String[] MANAGED_CATEGORIES = {"core", "feature", "tweak", "action", "sampler"};

    @ConfigDoc(value = "Enables live hotloading for React managed configs.", impact = "Set to false to disable file watching and require manual reloads.")
    private boolean enabled = true;

    @ConfigDoc(value = "Polling interval used by the config watcher in milliseconds.", impact = "Lower values detect changes faster but perform more frequent scans.")
    private int pollIntervalMs = 500;

    @ConfigDoc(value = "Maximum number of key-level diff messages sent per changed file.", impact = "Lower values reduce operator chat noise on large config edits.")
    private int maxDiffMessagesPerFile = 12;

    @ConfigDoc(value = "Sends hotload change summaries to online operators.", impact = "Disable if you only want console logs and no in-game notifications.")
    private boolean notifyOperators = true;

    private transient File dataFolder;
    private transient File configToml;
    private transient File configLegacyJson;
    private final transient ConfigHotloadEngine hotloadEngine = new ConfigHotloadEngine(
            this::isManagedConfigFile,
            this::listKnownConfigFiles,
            this::readFileContent,
            this::normalizeContent
    );

    public HotloadController() {
        super("react", "hotload", 500);
    }

    @Override
    public String getName() {
        return "Hotload";
    }

    @Override
    public void start() {
        dataFolder = React.instance.getDataFolder();
        configToml = React.instance.getDataFile("config.toml");
        configLegacyJson = React.instance.getDataFile("config.json");
        reconfigureWatcher();
    }

    @Override
    public void stop() {
        hotloadEngine.clear();
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

        hotloadEngine.configure(effectivePollInterval, watchedFiles, watchedDirectories);
        React.info("Config hotload watcher enabled for config.toml and managed component configs.");
    }

    public void refreshAfterConfigReload() {
        reconfigureWatcher();
    }

    private void pollConfigChanges() {
        if (!enabled) {
            return;
        }

        Set<File> touched = hotloadEngine.pollTouchedFiles();
        if (touched.isEmpty()) {
            return;
        }

        for (File file : touched) {
            hotloadEngine.processFileChange(file, this::applyConfigChange, delta -> notifyOps(file, delta.before(), delta.after()));
        }
    }

    private boolean applyConfigChange(File file) {
        try {
            if (isShadowedLegacyJson(file)) {
                React.verbose("Ignoring legacy json hotload because canonical toml exists: " + file.getPath());
                return false;
            }

            if (isMainConfigFile(file)) {
                boolean ok = ReactConfiguration.reload();
                if (ok) {
                    refreshGlobalRuntimeSettings();
                } else {
                    React.warn("Skipped hotload for " + file.getPath() + " due to invalid config.");
                }
                return ok;
            }

            ManagedConfig target = resolveManagedConfig(file);
            if (target != null) {
                return switch (target.category) {
                    case "core" -> reloadCoreConfig(target.id, file);
                    case "feature" -> reloadFeatureConfig(target.id, file);
                    case "tweak" -> reloadTweakConfig(target.id, file);
                    case "action" -> reloadActionConfig(target.id, file);
                    case "sampler" -> reloadSamplerConfig(target.id, file);
                    default -> validateAndCanonicalizeConfig(file);
                };
            }

            return validateAndCanonicalizeConfig(file);
        } catch (Throwable e) {
            React.warn("Skipped hotload for " + file.getPath() + " due to invalid config: " + e.getMessage());
            return false;
        }
    }

    private boolean reloadCoreConfig(String id, File file) {
        if (id == null || React.instance.getControllerRegistry() == null) {
            return validateAndCanonicalizeConfig(file);
        }

        IController controller = React.instance.getControllerRegistry().get(id);
        if (controller == null) {
            return validateAndCanonicalizeConfig(file);
        }

        return runSync(() -> {
            controller.loadConfiguration();
            if (controller == this) {
                reconfigureWatcher();
            }
            if (controller instanceof PlayerController playerController) {
                playerController.updateMonitors();
            }
            return true;
        });
    }

    private boolean reloadFeatureConfig(String id, File file) {
        FeatureController controller = React.controller(FeatureController.class);
        if (controller == null || controller.getFeatures() == null || id == null) {
            return validateAndCanonicalizeConfig(file);
        }

        Feature feature = controller.getFeatures().get(id);
        if (feature == null) {
            return validateAndCanonicalizeConfig(file);
        }

        return runSync(() -> {
            boolean wasActive = controller.getActiveFeatures().containsKey(feature.getId());
            feature.loadConfiguration();
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
        });
    }

    private boolean reloadTweakConfig(String id, File file) {
        TweakController controller = React.controller(TweakController.class);
        if (controller == null || controller.getTweaks() == null || id == null) {
            return validateAndCanonicalizeConfig(file);
        }

        Tweak tweak = controller.getTweaks().get(id);
        if (tweak == null) {
            return validateAndCanonicalizeConfig(file);
        }

        return runSync(() -> {
            boolean wasActive = controller.getActiveTweaks().containsKey(tweak.getId());
            tweak.loadConfiguration();
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
        });
    }

    private boolean reloadActionConfig(String id, File file) {
        ActionController controller = React.controller(ActionController.class);
        if (controller == null || controller.getActions() == null || id == null) {
            return validateAndCanonicalizeConfig(file);
        }

        Action<?> action = controller.getActions().get(id);
        if (action == null) {
            return validateAndCanonicalizeConfig(file);
        }

        return runSync(() -> {
            boolean wasEnabled = action.isEnabled();
            action.loadConfiguration();
            if (!wasEnabled && action.isEnabled()) {
                action.onInit();
            }
            return true;
        });
    }

    private boolean reloadSamplerConfig(String id, File file) {
        SampleController controller = React.controller(SampleController.class);
        if (controller == null || controller.getSamplers() == null || id == null) {
            return validateAndCanonicalizeConfig(file);
        }

        if (controller.getSamplers().get(id) == null) {
            return validateAndCanonicalizeConfig(file);
        }

        return runSync(() -> {
            boolean ok = controller.reloadSamplerConfig(id);
            if (!ok) {
                return false;
            }

            PlayerController playerController = React.controller(PlayerController.class);
            if (playerController != null) {
                playerController.updateMonitors();
            }

            return true;
        });
    }

    private boolean runSync(BooleanSupplier supplier) {
        return J.sResult(() -> {
            try {
                return supplier.getAsBoolean();
            } catch (Throwable e) {
                React.warn("Hotload apply failed: " + e.getMessage());
                return false;
            }
        });
    }

    private void refreshGlobalRuntimeSettings() {
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

    private boolean validateAndCanonicalizeConfig(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return true;
        }

        try {
            String raw = readFileContent(file);
            JsonElement parsed = parseStructured(raw, file);
            if (parsed == null) {
                return false;
            }

            if (ConfigFileSupport.isTomlFile(file)) {
                return true;
            }

            String canonical = new GsonBuilder().setPrettyPrinting().create().toJson(parsed);
            if (!normalizeContent(raw).equals(normalizeContent(canonical))) {
                ConfigRewriteReporter.reportRewrite(file, "hotload", raw, canonical);
                IO.writeAll(file, canonical);
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
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
        if (file == null || !ConfigFileSupport.isSupportedConfigFile(file)) {
            return false;
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

        return files;
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

        try {
            return Files.readString(file.toPath());
        } catch (Throwable e) {
            return null;
        }
    }

    private String normalizeContent(String text) {
        if (text == null) {
            return null;
        }
        return ConfigFileSupport.normalize(text);
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
        List<String> messages = new ArrayList<>();
        int shown = Math.min(Math.max(1, maxDiffMessagesPerFile), diffs.size());
        for (int i = 0; i < shown; i++) {
            ConfigHotloadEngine.DiffEntry diff = diffs.get(i);
            messages.add(formatHotloadMessage(relative, diff.key(), diff.oldValue(), diff.newValue()));
        }

        if (diffs.size() > shown) {
            int remaining = diffs.size() - shown;
            messages.add(formatHotloadMessage(relative, "...", "+" + remaining + " more", "truncated"));
        }

        J.s(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOp()) {
                    continue;
                }

                messages.forEach(player::sendMessage);
            }
        });
    }

    private String formatHotloadMessage(String file, String key, String oldValue, String newValue) {
        return C.GRAY + "[" + C.AQUA + "React" + C.GRAY + "]: "
                + C.GREEN + "Config Hotloaded: "
                + C.WHITE + "[" + file + "] "
                + C.AQUA + "[" + key + "] "
                + C.GRAY + "[" + formatValue(oldValue) + " -> " + formatValue(newValue) + "]";
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

    private record ManagedConfig(String category, String id) {
    }
}
