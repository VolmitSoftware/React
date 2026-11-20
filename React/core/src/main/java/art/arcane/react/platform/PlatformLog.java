package art.arcane.react.platform;

/** Logging through the bound platform; silent when none is bound. */
public final class PlatformLog {
  private PlatformLog() {
  }

  public static void warn(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.warn(message);
    }
  }

  public static void warn(String message, Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.warn(message, failure);
    }
  }

  public static void reportError(String context, Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(context, failure);
    }
  }
}
