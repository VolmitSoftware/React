package art.arcane.react.platform;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Holds the active platform. The platform module binds one at startup. Until then, and after unbind, the first
 * {@link ReactPlatform} service on React's own class path stands in, so static facades work during load and in tests.
 */
public final class ReactPlatforms {
  private static volatile ReactPlatform bound;
  private static volatile ReactPlatform fallback;
  private static volatile boolean fallbackResolved;

  private ReactPlatforms() {
  }

  public static synchronized void bind(ReactPlatform platform) {
    if (platform == null) {
      throw new IllegalArgumentException("platform");
    }
    ReactPlatform current = bound;
    if (current != null && current != platform) {
      throw new IllegalStateException("React platform is already bound to " + current.platformName());
    }
    bound = platform;
  }

  public static synchronized void unbind() {
    bound = null;
  }

  public static boolean isBound() {
    return bound != null;
  }

  public static ReactPlatform get() {
    ReactPlatform platform = getOrNull();
    if (platform == null) {
      throw new IllegalStateException("No React platform is bound");
    }
    return platform;
  }

  public static ReactPlatform getOrNull() {
    ReactPlatform platform = bound;
    return platform != null ? platform : fallback();
  }

  private static ReactPlatform fallback() {
    if (fallbackResolved) {
      return fallback;
    }
    synchronized (ReactPlatforms.class) {
      if (!fallbackResolved) {
        fallback = loadService();
        fallbackResolved = true;
      }
      return fallback;
    }
  }

  private static ReactPlatform loadService() {
    try {
      Iterator<ReactPlatform> services = ServiceLoader.load(ReactPlatform.class, ReactPlatforms.class.getClassLoader()).iterator();
      return services.hasNext() ? services.next() : null;
    } catch (Throwable ignored) {
      return null;
    }
  }
}
