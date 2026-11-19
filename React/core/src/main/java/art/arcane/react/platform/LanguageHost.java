package art.arcane.react.platform;

import art.arcane.volmlib.util.localization.MessageCatalog;

import java.io.File;
import java.util.logging.Logger;

/**
 * Platform settings and file hooks behind React's localization.
 */
public interface LanguageHost {
  /**
   * The server language from React's configuration, such as {@code en_US}.
   */
  String configuredLanguage();

  /**
   * Writes {@code locale} as the configured server language and applies it to the live configuration.
   */
  void persistServerLanguage(String locale) throws Exception;

  /**
   * Starts downloading a language that is configured but not installed yet.
   */
  void requestLanguageInstall(String locale);

  /**
   * Records a file React wrote itself, so file watchers do not treat it as an operator edit.
   */
  void noteSelfWrite(File file, String content);

  Logger logger();

  default void verbose(String message, Throwable failure) {
    ReactPlatforms.get().verbose(message + ": " + failure);
  }

  default void warn(String message, Throwable failure) {
    ReactPlatforms.get().warn(message + ": " + failure);
  }

  default void error(String message) {
    ReactPlatforms.get().warn(message);
  }

  /**
   * Adds the platform's own message keys, such as generated configuration descriptions, to the catalog.
   */
  default void addCatalogEntries(MessageCatalog.Builder builder) {
  }
}
