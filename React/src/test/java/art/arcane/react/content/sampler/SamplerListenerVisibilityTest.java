package art.arcane.react.content.sampler;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

class SamplerListenerVisibilityTest {
  private static final String PACKAGE = "art.arcane.react.content.sampler";

  @Test
  void everyEventHandlerInTheSamplerPackageIsRegistrableByBukkit() throws Exception {
    List<Class<?>> classes = samplerClasses();
    List<String> violations = new ArrayList<>();
    int handlers = 0;

    for (Class<?> type : classes) {
      for (Method method : type.getDeclaredMethods()) {
        if (!method.isAnnotationPresent(EventHandler.class) || method.isBridge() || method.isSynthetic()) {
          continue;
        }

        handlers++;
        if (!isPubliclyReachable(type)) {
          violations.add(type.getName() + " declares @EventHandler " + method.getName() + " but the class is not public");
        }
        if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
          violations.add(type.getName() + "#" + method.getName() + " must be a public instance method");
        }
        if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
          violations.add(type.getName() + "#" + method.getName() + " must take exactly one Event parameter");
        }
      }
    }

    Assertions.assertTrue(handlers > 0, "no @EventHandler methods found in " + PACKAGE);
    Assertions.assertTrue(violations.isEmpty(), String.join("\n", violations));
  }

  private static boolean isPubliclyReachable(Class<?> type) {
    Class<?> current = type;
    while (current != null) {
      if (!Modifier.isPublic(current.getModifiers())) {
        return false;
      }
      current = current.getEnclosingClass();
    }
    return true;
  }

  private static List<Class<?>> samplerClasses() throws Exception {
    ClassLoader loader = SamplerListenerVisibilityTest.class.getClassLoader();
    Enumeration<URL> roots = loader.getResources(PACKAGE.replace('.', '/'));
    List<Class<?>> classes = new ArrayList<>();
    while (roots.hasMoreElements()) {
      URL root = roots.nextElement();
      if (!"file".equals(root.getProtocol())) {
        continue;
      }
      Path directory = Path.of(root.toURI());
      try (Stream<Path> files = Files.list(directory)) {
        List<Path> classFiles = files
            .filter(path -> path.getFileName().toString().endsWith(".class"))
            .sorted()
            .toList();
        for (Path classFile : classFiles) {
          String simpleName = classFile.getFileName().toString();
          String className = PACKAGE + "." + simpleName.substring(0, simpleName.length() - ".class".length());
          classes.add(Class.forName(className, false, loader));
        }
      }
    }

    Assertions.assertFalse(classes.isEmpty(), "no classes found for " + PACKAGE);
    return classes;
  }
}
