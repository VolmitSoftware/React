package art.arcane.react;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class ListenerSpigotCompatibilityTest {
  private static final String SPIGOT_CLASSPATH_PROPERTY = "react.spigotApiClasspath";
  private static final String ROOT_PACKAGE = "art.arcane.react";
  private static final List<String> SERVER_API_PACKAGES = List.of(
      "org.bukkit.", "org.spigotmc.", "io.papermc.", "com.destroystokyo.paper.", "net.md_5.");

  @Test
  void onlyPaperNamedListenersHandleEventsThatSpigotLacks() throws Exception {
    String spigotClasspath = System.getProperty(SPIGOT_CLASSPATH_PROPERTY);
    Assumptions.assumeTrue(spigotClasspath != null && !spigotClasspath.isBlank(),
        SPIGOT_CLASSPATH_PROPERTY + " is set by the Gradle test task");

    List<String> violations = new ArrayList<>();
    int handlers = 0;
    try (URLClassLoader spigot = spigotLoader(spigotClasspath)) {
      Assertions.assertTrue(spigotHas(spigot, "org.bukkit.event.world.ChunkLoadEvent"), "Spigot API classpath is incomplete");
      for (Class<?> listener : listenerClasses()) {
        for (Method method : listener.getDeclaredMethods()) {
          if (!method.isAnnotationPresent(EventHandler.class) || method.isBridge() || method.isSynthetic()
              || method.getParameterCount() != 1) {
            continue;
          }

          handlers++;
          String eventType = method.getParameterTypes()[0].getName();
          if (isServerApiType(eventType) && !spigotHas(spigot, eventType)
              && !listener.getSimpleName().contains("Paper")) {
            violations.add(listener.getName() + "#" + method.getName() + " handles " + eventType
                + ", which Spigot lacks; move it into a probe-registered Paper listener");
          }
        }
      }
    }

    Assertions.assertTrue(handlers > 0, "no @EventHandler methods found under " + ROOT_PACKAGE);
    Assertions.assertTrue(violations.isEmpty(), String.join("\n", violations));
  }

  private static URLClassLoader spigotLoader(String classpath) throws MalformedURLException {
    String[] entries = classpath.split(File.pathSeparator);
    List<URL> urls = new ArrayList<>(entries.length);
    for (String entry : entries) {
      if (!entry.isBlank()) {
        urls.add(Path.of(entry).toUri().toURL());
      }
    }
    return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
  }

  private static boolean isServerApiType(String className) {
    for (String serverPackage : SERVER_API_PACKAGES) {
      if (className.startsWith(serverPackage)) {
        return true;
      }
    }
    return false;
  }

  private static boolean spigotHas(ClassLoader spigot, String className) {
    try {
      Class.forName(className, false, spigot);
      return true;
    } catch (ClassNotFoundException | LinkageError missing) {
      return false;
    }
  }

  private static List<Class<?>> listenerClasses() throws IOException, URISyntaxException, ClassNotFoundException {
    Path root = Path.of(React.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    ClassLoader loader = ListenerSpigotCompatibilityTest.class.getClassLoader();
    List<Class<?>> listeners = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root.resolve(ROOT_PACKAGE.replace('.', '/')))) {
      List<Path> classFiles = files
          .filter(path -> path.getFileName().toString().endsWith(".class"))
          .sorted()
          .toList();
      for (Path classFile : classFiles) {
        String relative = root.relativize(classFile).toString();
        String className = relative.substring(0, relative.length() - ".class".length())
            .replace(File.separatorChar, '.');
        Class<?> type = Class.forName(className, false, loader);
        if (Listener.class.isAssignableFrom(type)) {
          listeners.add(type);
        }
      }
    }

    Assertions.assertFalse(listeners.isEmpty(), "no listener classes found under " + ROOT_PACKAGE);
    return listeners;
  }
}
