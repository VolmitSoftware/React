package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.platform.RegistryHost;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.volmlib.util.io.JarScanner;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

final class BukkitRegistryHost implements RegistryHost {
  @Override
  public Collection<Class<?>> discoverClasses(String packageName) throws IOException {
    String path = React.instance.jar().getAbsolutePath().replaceAll("\\Q.jar.jar\\E", ".jar");
    JarScanner scanner = new JarScanner(new File(path), packageName);
    scanner.scan();
    return scanner.getClasses();
  }

  @Override
  public void deferStartupTask(Runnable task) {
    React.instance.getStartupTasks().add(task);
  }

  @Override
  public File configFile(String category, String fileName) {
    return React.instance.getDataFile(category, fileName);
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Object loadConfiguration(File file, Class<?> type, Object defaults, boolean overwriteOnReadFailure,
                                  String sourceTag, String createdMessage) throws IOException {
    return ConfigFileSupport.load(file, null, (Class) type, defaults, overwriteOnReadFailure, sourceTag, createdMessage);
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Object parseConfiguration(File sourceFile, String rawContent, Class<?> type, String sourceTag) throws IOException {
    return ConfigFileSupport.parseSnapshot(sourceFile, rawContent, (Class) type, sourceTag);
  }
}
