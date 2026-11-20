package art.arcane.react.mod;

import art.arcane.react.api.rendering.MapSettings;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.platform.LanguageHost;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.react.util.project.config.ConfigLocalization;
import art.arcane.volmlib.util.localization.MessageCatalog;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * The server language lives in {@code react.toml} under {@code language}, as on Bukkit. The file is read only after the
 * platform is bound, so the language catalog already holds the config documentation that its comments are written from.
 */
final class ModLanguageHost implements LanguageHost {
    static final String SOURCE_TAG = "main-config";
    private static final Logger LOGGER = Logger.getLogger(ReactModRuntime.NAME);
    private static final List<Class<?>> DOCUMENTED_TYPES = List.of(MapSettings.class, WebConfiguration.class);
    private final File settingsFile;
    private volatile Settings settings = new Settings();

    @ConfigDescription("Global React configuration. Controls default monitoring layout, value model tuning, and diagnostics behavior.")
    static final class Settings {
        @ConfigDoc(value = "Locale used for React player and operator interfaces.", impact = "Code-owned English remains the fallback; editable TOML catalogs are loaded from languages.")
        String language = "en_US";
    }

    ModLanguageHost(File dataFolder) {
        this.settingsFile = new File(dataFolder, "react.toml");
    }

    void load() throws IOException {
        settings = ModConfigFiles.load(settingsFile, Settings.class, new Settings(), SOURCE_TAG, true, LOGGER::info);
    }

    @Override
    public String configuredLanguage() {
        return settings.language;
    }

    @Override
    public void persistServerLanguage(String locale) throws Exception {
        Settings next = ModConfigFiles.load(settingsFile, Settings.class, new Settings(), SOURCE_TAG, true, LOGGER::info);
        next.language = locale;
        ModConfigFiles.save(settingsFile, next, SOURCE_TAG);
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
