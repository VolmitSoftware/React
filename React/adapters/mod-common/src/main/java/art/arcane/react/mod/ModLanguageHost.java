package art.arcane.react.mod;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.platform.LanguageHost;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/** The server language lives in {@code react.toml} under {@code language}, as on Bukkit. */
final class ModLanguageHost implements LanguageHost {
    private static final Logger LOGGER = Logger.getLogger(ReactModRuntime.NAME);
    private final File settingsFile;
    private volatile Settings settings;

    static final class Settings {
        String language = "en_US";
    }

    ModLanguageHost(File dataFolder) throws IOException {
        this.settingsFile = new File(dataFolder, "react.toml");
        this.settings = ModConfigFiles.load(settingsFile, Settings.class, new Settings(), true, LOGGER::info);
    }

    @Override
    public String configuredLanguage() {
        return settings.language;
    }

    @Override
    public void persistServerLanguage(String locale) throws Exception {
        Settings next = ModConfigFiles.load(settingsFile, Settings.class, new Settings(), true, LOGGER::info);
        next.language = locale;
        ModConfigFiles.save(settingsFile, next);
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
}
