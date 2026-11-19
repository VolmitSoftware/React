package art.arcane.react.platform;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Finds the classes that registries instantiate (samplers, controllers, features) and runs
 * registry scans at the platform's startup checkpoint.
 */
public interface RegistryHost {
  Collection<Class<?>> discoverClasses(String packageName) throws IOException;

  void deferStartupTask(Runnable task);

  /**
   * The config file a registered object loads from, with its parent directory created.
   */
  File configFile(String category, String fileName);

  /**
   * Reads {@code file} into a new instance of {@code type}, writing defaults from {@code defaults}
   * for missing keys and creating the file when it does not exist.
   */
  Object loadConfiguration(File file, Class<?> type, Object defaults, boolean overwriteOnReadFailure,
                           String sourceTag, String createdMessage) throws IOException;

  /**
   * Parses already-read config text into a new instance of {@code type} without touching disk.
   */
  Object parseConfiguration(File sourceFile, String rawContent, Class<?> type, String sourceTag) throws IOException;
}
