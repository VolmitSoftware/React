package art.arcane.react.mod;

import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.api.monitor.MonitoringSettings;
import art.arcane.react.api.rendering.MapSettings;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.platform.LanguageHost;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.react.util.project.config.ConfigLocalization;
import art.arcane.react.util.project.config.TomlFiles;
import art.arcane.volmlib.util.localization.MessageCatalog;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * The server language lives in {@code react.toml} under {@code language}, as on Bukkit. The file is read only after the
 * platform is bound, so the language catalog already holds the config documentation that its comments are written from.
 * The entity priority model lives there too, under {@code priority} with Bukkit's keys, as it does in Bukkit's
 * {@code react.toml}.
 */
final class ModLanguageHost implements LanguageHost {
    static final String SOURCE_TAG = "main-config";
    private static final Logger LOGGER = Logger.getLogger(ReactModRuntime.NAME);
    private static final List<Class<?>> DOCUMENTED_TYPES = List.of(MapSettings.class, WebConfiguration.class, MonitoringSettings.class);
    private final File settingsFile;
    private volatile Settings settings = new Settings();
    private volatile BiConsumer<File, String> selfWrites;

    @ConfigDescription("Global React configuration. Controls default monitoring layout, value model tuning, and diagnostics behavior.")
    static final class Settings {
        @ConfigDoc(value = "Locale used for React player and operator interfaces.", impact = "Code-owned English remains the fallback; editable TOML catalogs are loaded from languages.")
        String language = "en_US";

        @ConfigDoc(value = "Entity priority model used by multiple React subsystems.", impact = "Changing these weights can alter culling, queueing, and visibility behavior.")
        EntityPriority priority = new EntityPriority();

        @ConfigDoc(value = "Enables plugin-gated secret integration features.", impact = "Disabled by default. Enable to allow Iris/Adapt secret bundles when dependencies are present.")
        boolean integrationSecretsEnabled = false;

        @ConfigDoc(value = "Default monitor layout shown to players.", impact = "Changes affect the baseline monitoring dashboard composition and sampler grouping.")
        MonitoringSettings monitoring = new MonitoringSettings();
    }

    ModLanguageHost(File dataFolder) {
        this.settingsFile = new File(dataFolder, "react.toml");
    }

    void load() throws IOException {
        settings = loaded(TomlFiles.load(settingsFile, Settings.class, new Settings(), SOURCE_TAG, true, LOGGER::info));
    }

    EntityPriority priority() {
        return settings.priority;
    }

    private static Settings loaded(Settings loaded) {
        if (loaded.priority == null) {
            loaded.priority = new EntityPriority();
        }
        loaded.priority.rebuildPriority();
        return loaded;
    }

    /**
     * Re-reads {@code react.toml} after an edit; a file that does not parse keeps the current settings.
     */
    boolean reload() {
        try {
            settings = loaded(TomlFiles.load(settingsFile, Settings.class, settings, SOURCE_TAG, false, LOGGER::info));
            return true;
        } catch (IOException | RuntimeException failure) {
            LOGGER.warning("Kept the current React settings; react.toml could not be read: " + failure.getMessage());
            return false;
        }
    }

    MonitoringSettings monitoring() {
        MonitoringSettings monitoring = settings.monitoring;
        return monitoring == null ? new MonitoringSettings() : monitoring;
    }

    Settings settings() {
        return settings;
    }

    File settingsFile() {
        return settingsFile;
    }

    boolean integrationSecretsEnabled() {
        return settings.integrationSecretsEnabled;
    }

    @Override
    public String configuredLanguage() {
        return settings.language;
    }

    @Override
    public void persistServerLanguage(String locale) throws Exception {
        Settings next = loaded(TomlFiles.load(settingsFile, Settings.class, new Settings(), SOURCE_TAG, true, LOGGER::info));
        next.language = locale;
        TomlFiles.save(settingsFile, next, SOURCE_TAG);
        settings = next;
    }

    @Override
    public void requestLanguageInstall(String locale) {
        ReactLanguage.requestInstall(locale).whenComplete((String content, Throwable failure) -> {
            if (failure != null) {
                LOGGER.warning("Could not install React language " + locale + ": " + failure.getMessage());
            } else {
                ReactLanguage.reload();
            }
        });
    }

    @Override
    public void noteSelfWrite(File file, String content) {
        BiConsumer<File, String> listener = selfWrites;
        if (listener != null) {
            listener.accept(file, content);
        }
    }

    /** Where language files React installs itself are reported, so the hotload does not announce them as edits. */
    void selfWrites(BiConsumer<File, String> listener) {
        selfWrites = listener;
    }

    @Override
    public Logger logger() {
        return LOGGER;
    }

    @Override
    public void addCatalogEntries(MessageCatalog.Builder builder) {
        ConfigLocalization.addTo(builder, DOCUMENTED_TYPES);
    }
}
