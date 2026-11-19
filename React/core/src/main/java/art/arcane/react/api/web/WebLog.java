package art.arcane.react.api.web;

import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;

/** React Web's console lines, sent to the bound platform and dropped when none is bound. */
public final class WebLog {
  private WebLog() {
  }

  public static void info(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.info(message);
    }
  }

  public static void warn(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.warn(message);
    }
  }

  public static void warn(String message, Throwable failure) {
    warn(failure == null ? message : message + " (" + failure + ")");
  }

  public static void verbose(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.verbose(message);
    }
  }

  public static void verbose(String message, Throwable failure) {
    verbose(failure == null ? message : message + " (" + failure + ")");
  }

  public static void reportError(String context, Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(context, failure);
    }
  }
}
