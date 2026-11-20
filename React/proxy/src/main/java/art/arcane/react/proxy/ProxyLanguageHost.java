package art.arcane.react.proxy;

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
import java.util.logging.Logger;

/** The proxy's language lives in {@code react.toml} under {@code language}, as on Bukkit and mod servers. */
final class ProxyLanguageHost implements LanguageHost {
  static final String SOURCE_TAG = "main-config";
  private static final List<Class<?>> DOCUMENTED_TYPES = List.of(WebConfiguration.class);
  private final File settingsFile;
  private final Logger logger;
  private volatile Settings settings = new Settings();

  @ConfigDescription("Global React configuration. Controls default monitoring layout, value model tuning, and diagnostics behavior.")
  static final class Settings {
    @ConfigDoc(value = "Locale used for React player and operator interfaces.", impact = "Code-owned English remains the fallback; editable TOML catalogs are loaded from languages.")
    String language = "en_US";
  }

  ProxyLanguageHost(File dataFolder, Logger logger) {
    this.settingsFile = new File(dataFolder, "react.toml");
    this.logger = logger;
  }

  void load() throws IOException {
    settings = TomlFiles.load(settingsFile, Settings.class, new Settings(), SOURCE_TAG, true, logger::info);
  }

  @Override
  public String configuredLanguage() {
    return settings.language;
  }

  @Override
  public void persistServerLanguage(String locale) throws Exception {
    Settings next = TomlFiles.load(settingsFile, Settings.class, new Settings(), SOURCE_TAG, true, logger::info);
    next.language = locale;
    TomlFiles.save(settingsFile, next, SOURCE_TAG);
    settings = next;
  }

  @Override
  public void requestLanguageInstall(String locale) {
    ReactLanguage.requestInstall(locale).whenComplete((String content, Throwable failure) -> {
      if (failure != null) {
        logger.warning("Could not install React language " + locale + ": " + failure.getMessage());
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
    return logger;
  }

  @Override
  public void addCatalogEntries(MessageCatalog.Builder builder) {
    ConfigLocalization.addTo(builder, DOCUMENTED_TYPES);
  }
}
