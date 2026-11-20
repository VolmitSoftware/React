package art.arcane.react.mod;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.WebRevisions;
import art.arcane.react.core.controller.ComponentConfigEditor;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.util.project.config.ConfigFileWatcher;
import art.arcane.react.util.project.config.TomlFiles;
import art.arcane.react.util.project.registry.Registered;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Applies operator edits to React's config files while the server runs, as React's hotload does on Bukkit:
 * {@code react.toml}, language files, {@code core/map.toml} and every feature, tweak, action and sampler config.
 * Operators see one chat line per changed key. {@code web.toml} is left to React Web.
 */
final class ModConfigHotload {
    private static final String[] COMPONENT_CATEGORIES = {"feature", "tweak", "action", "sampler"};
    private static final int POLL_TICKS = 10;

    private final MinecraftServer server;
    private final File dataFolder;
    private final ModLanguageHost language;
    private final ModMaps maps;
    private final Supplier<SampleController> samples;
    private final ModReactPlatform platform;
    private final ModTicks ticks;
    private final ConfigFileWatcher watcher = new ConfigFileWatcher(this::files);
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile boolean running;
    private int untilPoll = POLL_TICKS;

    ModConfigHotload(MinecraftServer server, File dataFolder, ModLanguageHost language, ModMaps maps,
                     Supplier<SampleController> samples, ModReactPlatform platform, ModTicks ticks) {
        this.server = server;
        this.dataFolder = dataFolder;
        this.language = language;
        this.maps = maps;
        this.samples = samples;
        this.platform = platform;
        this.ticks = ticks;
    }

    void start() {
        TomlFiles.setSelfWriteListener(watcher::accept);
        language.selfWrites(watcher::accept);
        running = true;
        polling.set(true);
        submit(() -> {
            watcher.prime();
            polling.set(false);
        });
        platform.verbose("Config hotload watcher enabled for react.toml, language files and managed component configs.");
    }

    void stop() {
        running = false;
        TomlFiles.setSelfWriteListener(null);
        language.selfWrites(null);
        watcher.clear();
    }

    void tick() {
        if (!running || --untilPoll > 0) {
            return;
        }
        untilPoll = POLL_TICKS;
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        submit(() -> {
            List<ConfigFileWatcher.Change> changes = watcher.poll();
            if (changes.isEmpty()) {
                polling.set(false);
                return;
            }
            ticks.later(() -> {
                try {
                    for (ConfigFileWatcher.Change change : changes) {
                        apply(change);
                    }
                } finally {
                    polling.set(false);
                }
            });
        });
    }

    private void submit(Runnable work) {
        try {
            platform.asyncExecutor().execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException | LinkageError failure) {
                    polling.set(false);
                    platform.warn("React hotload IO worker failed: " + failure.getMessage(), failure);
                }
            });
        } catch (RejectedExecutionException rejected) {
            polling.set(false);
        }
    }

    private List<File> files() {
        List<File> files = new ArrayList<>();
        files.add(language.settingsFile());
        addToml(files, new File(dataFolder, "core"));
        for (String category : COMPONENT_CATEGORIES) {
            addToml(files, new File(dataFolder, category));
        }
        addToml(files, ReactLanguage.languageFolder());
        return files;
    }

    private static void addToml(List<File> files, File folder) {
        File[] children = folder == null ? null : folder.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isFile() && child.getName().toLowerCase(Locale.ROOT).endsWith(".toml")) {
                files.add(child);
            }
        }
    }

    private void apply(ConfigFileWatcher.Change change) {
        if (!running) {
            return;
        }
        File file = change.file();
        Applied applied;
        try {
            applied = applyFile(file, change.after());
        } catch (RuntimeException | LinkageError failure) {
            platform.warn("Could not hotload " + relative(file) + ": " + failure.getMessage(), failure);
            applied = Applied.REJECTED;
        }
        watcher.accept(file, current(file));
        if (applied.announce) {
            try {
                notifyOperators(ConfigFileWatcher.notices(relative(file), change.before(), change.after(), applied.type));
            } catch (RuntimeException | LinkageError failure) {
                platform.warn("Could not announce the hotload of " + relative(file) + ": " + failure.getMessage(), failure);
            }
        }
    }

    private Applied applyFile(File file, String after) {
        File folder = file.getParentFile();
        if (same(file, language.settingsFile())) {
            if (after == null) {
                return Applied.IGNORED;
            }
            return language.reload() && ReactLanguage.reload() ? new Applied(true, ModLanguageHost.Settings.class) : Applied.REJECTED;
        }
        if (same(folder, ReactLanguage.languageFolder())) {
            return ReactLanguage.reload() ? new Applied(true, null) : Applied.REJECTED;
        }
        if (after == null || folder == null) {
            return Applied.IGNORED;
        }
        String category = folder.getName();
        String id = file.getName().substring(0, file.getName().length() - ".toml".length());
        if (category.equals("core")) {
            if (!id.equals("map")) {
                return Applied.IGNORED;
            }
            return maps.reloadSettings() ? new Applied(true, maps.settings().getClass()) : Applied.REJECTED;
        }
        Registered target = switch (category) {
            case "feature" -> ReactComponents.feature(id);
            case "tweak" -> ReactComponents.tweak(id);
            case "action" -> ReactComponents.action(id);
            case "sampler" -> sampler(id);
            default -> null;
        };
        if (target == null) {
            return Applied.IGNORED;
        }
        try {
            TomlFiles.parse(after, target.getClass());
        } catch (IOException | RuntimeException invalid) {
            platform.warn("Rejected hotload of " + relative(file) + ": " + invalid.getMessage());
            return Applied.REJECTED;
        }
        boolean ok = target instanceof Sampler
                ? samples.get() != null && samples.get().reloadSamplerConfig(id)
                : ComponentConfigEditor.reload(target);
        if (!ok) {
            return Applied.REJECTED;
        }
        WebRevisions.bumpForConfigFile(category, id);
        return new Applied(true, target.getClass());
    }

    private Sampler sampler(String id) {
        SampleController controller = samples.get();
        return controller == null || controller.getSamplers() == null ? null : controller.getSamplers().get(id);
    }

    private void notifyOperators(List<ConfigFileWatcher.Notice> notices) {
        if (notices.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!VanillaApi.gamemaster(player)) {
                continue;
            }
            for (ConfigFileWatcher.Notice notice : notices) {
                ReactLanguage.sendPrefixed(player, notice.key(), notice.arguments());
            }
        }
    }

    private String relative(File file) {
        try {
            return dataFolder.getAbsoluteFile().toPath().normalize().relativize(file.getAbsoluteFile().toPath().normalize()).toString();
        } catch (IllegalArgumentException failure) {
            return file.getName();
        }
    }

    private static boolean same(File a, File b) {
        return a != null && b != null && a.getAbsoluteFile().toPath().normalize().equals(b.getAbsoluteFile().toPath().normalize());
    }

    private static String current(File file) {
        try {
            return file.isFile() ? Files.readString(file.toPath(), StandardCharsets.UTF_8) : null;
        } catch (IOException failure) {
            return null;
        }
    }

    private record Applied(boolean announce, Class<?> type) {
        static final Applied IGNORED = new Applied(false, null);
        static final Applied REJECTED = new Applied(false, null);
    }
}
