package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.platform.LanguageHost;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.react.util.project.config.ConfigLocalization;
import art.arcane.react.util.project.config.TomlCodec;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.MessageCatalog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.file.Files;
import java.util.logging.Logger;

public final class BukkitLanguageHost implements LanguageHost {
  @Override
  public String configuredLanguage() {
    return ReactConfiguration.get().getLanguage();
  }

  @Override
  public void persistServerLanguage(String locale) throws Exception {
    File file = React.instance.getDataFile("react.toml");
    JsonObject configuration = ConfigFileSupport.parseToJsonElement(Files.readString(file.toPath()), file).getAsJsonObject();
    configuration.addProperty("language", locale);
    ReactConfiguration next = new Gson().fromJson(configuration, ReactConfiguration.class);
    String raw = TomlCodec.toToml(next, "main-config");
    AtomicFileIO.writeString(file.toPath(), raw);
    ReactConfiguration.applyHotloadSnapshot(next);
    ConfigFileSupport.noteSelfWrite(file, raw);
  }

  @Override
  public void requestLanguageInstall(String locale) {
    React.instance.requestLanguageInstall(locale);
  }

  @Override
  public void noteSelfWrite(File file, String content) {
    ConfigFileSupport.noteSelfWrite(file, content);
  }

  @Override
  public Logger logger() {
    return React.instance.getLogger();
  }

  @Override
  public void verbose(String message, Throwable failure) {
    React.verbose(message, failure);
  }

  @Override
  public void warn(String message, Throwable failure) {
    React.warn(message, failure);
  }

  @Override
  public void error(String message) {
    React.error(message);
  }

  @Override
  public void addCatalogEntries(MessageCatalog.Builder builder) {
    ConfigLocalization.addTo(builder);
  }
}
