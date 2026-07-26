package art.arcane.react.api;

import art.arcane.react.api.metric.ReactMetrics;
import art.arcane.react.api.metric.internal.MetricBinding;
import art.arcane.react.api.metric.internal.MetricInstaller;
import art.arcane.react.api.protect.ReactProtection;
import art.arcane.react.api.protect.internal.ProtectionBinding;
import art.arcane.react.api.protect.internal.ProtectionInstaller;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

class ReactApiSurfaceTest {
  private static final List<String> API_PACKAGES = List.of(
      "art.arcane.react.api.protect",
      "art.arcane.react.api.metric");

  private static final List<String> BANNED_PREFIXES = List.of(
      "art.arcane.volmlib",
      "art.arcane.chrono",
      "art.arcane.curse",
      "art.arcane.multiburst",
      "net.bytebuddy",
      "io.github.slimjar",
      "net.kyori",
      "com.google.common",
      "it.unimi.dsi.fastutil");

  private static Path classesRoot() throws URISyntaxException {
    URI location = ReactProtection.class.getProtectionDomain().getCodeSource().getLocation().toURI();
    return Path.of(location);
  }

  private static List<Class<?>> apiClasses() throws IOException, URISyntaxException {
    Path root = classesRoot();
    List<Class<?>> found = new ArrayList<>();

    for (String apiPackage : API_PACKAGES) {
      Path directory = root.resolve(apiPackage.replace('.', '/'));

      if (!Files.isDirectory(directory)) {
        continue;
      }

      try (Stream<Path> files = Files.list(directory)) {
        for (Path file : files.toList()) {
          String name = file.getFileName().toString();

          if (!name.endsWith(".class") || name.contains("$")) {
            continue;
          }

          Class<?> type = Class.forName(apiPackage + "." + name.substring(0, name.length() - 6));

          if (Modifier.isPublic(type.getModifiers())) {
            found.add(type);
          }
        }
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(e);
      }
    }

    return found;
  }

  private static String rootComponentName(Class<?> type) {
    Class<?> current = type;

    while (current.isArray()) {
      current = current.getComponentType();
    }

    return current.getName();
  }

  private static boolean isAllowed(Class<?> type) {
    if (type.isPrimitive()) {
      return true;
    }

    String name = rootComponentName(type);

    if (name.startsWith("java.")) {
      return true;
    }

    if (name.startsWith("org.bukkit.")) {
      return true;
    }

    for (String apiPackage : API_PACKAGES) {
      if (name.startsWith(apiPackage + ".")) {
        return !name.contains(".internal.");
      }
    }

    return false;
  }

  @Test
  void theApiPackagesAreDiscoverableAndNonEmpty() throws Exception {
    List<Class<?>> classes = apiClasses();
    Assertions.assertTrue(classes.size() >= 10, "expected the api surface to be discovered, got " + classes);
    Assertions.assertTrue(classes.contains(ReactProtection.class));
    Assertions.assertTrue(classes.contains(ReactMetrics.class));
  }

  @Test
  void noPublicApiMemberReferencesAnInternalRelocatedOrAdventureType() throws Exception {
    List<String> violations = new ArrayList<>();

    for (Class<?> type : apiClasses()) {
      for (Class<?> supertype : supertypesOf(type)) {
        if (!isAllowed(supertype)) {
          violations.add(type.getName() + " extends/implements " + supertype.getName());
        }
      }

      for (Method method : type.getDeclaredMethods()) {
        if (!isVisible(method.getModifiers()) || method.isSynthetic()) {
          continue;
        }

        check(violations, type.getName() + "#" + method.getName() + " returns", method.getReturnType());

        for (Class<?> parameter : method.getParameterTypes()) {
          check(violations, type.getName() + "#" + method.getName() + " takes", parameter);
        }

        for (Class<?> thrown : method.getExceptionTypes()) {
          check(violations, type.getName() + "#" + method.getName() + " throws", thrown);
        }
      }

      for (Constructor<?> constructor : type.getDeclaredConstructors()) {
        if (!isVisible(constructor.getModifiers()) || constructor.isSynthetic()) {
          continue;
        }

        for (Class<?> parameter : constructor.getParameterTypes()) {
          check(violations, type.getName() + " constructor takes", parameter);
        }
      }

      for (Field field : type.getDeclaredFields()) {
        if (!isVisible(field.getModifiers()) || field.isSynthetic()) {
          continue;
        }

        check(violations, type.getName() + "#" + field.getName() + " is", field.getType());
      }
    }

    Assertions.assertTrue(violations.isEmpty(), "public API surface leaks non-portable types: " + violations);
  }

  @Test
  void noApiClassIsLoadedFromARelocatablePackage() throws Exception {
    for (Class<?> type : apiClasses()) {
      String name = type.getName().toLowerCase(Locale.ROOT);

      for (String banned : BANNED_PREFIXES) {
        Assertions.assertFalse(name.startsWith(banned.toLowerCase(Locale.ROOT)),
            type.getName() + " lives in a relocated package");
      }
    }
  }

  @Test
  void noPublishedApiTypeCanInstallOrReachTheBinding() throws Exception {
    List<Class<?>> installers = List.of(
        ProtectionInstaller.class, MetricInstaller.class, ProtectionBinding.class, MetricBinding.class);

    for (Class<?> installer : installers) {
      Assertions.assertTrue(installer.getName().contains(".internal."),
          installer.getName() + " must stay inside an internal package");
    }

    List<String> violations = new ArrayList<>();

    for (Class<?> type : apiClasses()) {
      for (Method method : type.getDeclaredMethods()) {
        if (!isVisible(method.getModifiers()) || method.isSynthetic()) {
          continue;
        }

        List<Class<?>> mentioned = new ArrayList<>(List.of(method.getReturnType()));
        mentioned.addAll(List.of(method.getParameterTypes()));

        for (Class<?> mention : mentioned) {
          if (installers.contains(mention)) {
            violations.add(type.getName() + "#" + method.getName() + " mentions " + mention.getName());
          }
        }
      }
    }

    Assertions.assertTrue(violations.isEmpty(),
        "the published facade must not let a third party install or read the binding: " + violations);
  }

  private static List<Class<?>> supertypesOf(Class<?> type) {
    List<Class<?>> out = new ArrayList<>();

    if (type.getSuperclass() != null && type.getSuperclass() != Object.class) {
      out.add(type.getSuperclass());
    }

    out.addAll(List.of(type.getInterfaces()));
    return out;
  }

  private static boolean isVisible(int modifiers) {
    return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
  }

  private static void check(List<String> violations, String what, Class<?> type) {
    if (!isAllowed(type)) {
      violations.add(what + " " + type.getName());
    }
  }
}
