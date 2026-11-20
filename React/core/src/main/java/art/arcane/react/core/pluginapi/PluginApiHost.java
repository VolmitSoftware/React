package art.arcane.react.core.pluginapi;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * What API packs need from the server: the pack folder, the installed plugins or mods they target, and the sources a
 * metric can read. Sources a platform lacks return NaN, or null for event counters.
 */
public interface PluginApiHost {
  /** The pack folder's name inside React's data folder. */
  String folderName();

  /** "Plugin API" or "Mod API", for log lines. */
  String label();

  /** The pack detail while the target is not running, such as {@code target-plugin-not-enabled}. */
  String targetMissingDetail();

  /** Example packs copied into a freshly created pack folder. */
  List<String> bundledExamples();

  /** Null when the example is not bundled. */
  InputStream bundledExample(String name) throws IOException;

  /** The item id for a pack icon, or null when the name is not an item. */
  String icon(String raw);

  /** The running target plugin or mod, or null. */
  Target target(String id);

  double integration(String integrationId, String key);

  double placeholder(String placeholder, boolean foliaSafe);

  /**
   * Counts every {@code eventClass} event into {@code increment} and returns the unregister action, or null when this
   * platform has no event counters.
   */
  Runnable eventCounter(Target target, String eventClass, Runnable increment) throws ReflectiveOperationException;

  record Target(String version, ClassLoader classLoader) {
  }
}
