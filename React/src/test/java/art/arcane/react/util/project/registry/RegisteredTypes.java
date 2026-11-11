package art.arcane.react.util.project.registry;

import art.arcane.react.React;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds every concrete Registered implementation compiled into React and builds instances of
 * them the way Registry does, falling back to constructor-free allocation for the ones the
 * plugin builds by hand.
 */
final class RegisteredTypes {
  private RegisteredTypes() {
  }

  enum Construction {
    PUBLIC_NO_ARG, HIDDEN_NO_ARG, ALLOCATED, UNAVAILABLE
  }

  record Built(Registered instance, Construction construction, Throwable failure) {
  }

  static Path mainClassesRoot() {
    try {
      return Path.of(React.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  static List<Class<? extends Registered>> concrete() {
    Path root = mainClassesRoot();
    if (!Files.isDirectory(root)) {
      throw new IllegalStateException("React main classes are not a directory: " + root);
    }
    List<Class<? extends Registered>> types = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(root)) {
      List<String> names = paths
          .filter(path -> path.toString().endsWith(".class"))
          .map(path -> root.relativize(path).toString())
          .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.').replace('\\', '.'))
          .filter(name -> name.startsWith("art.arcane.react."))
          .sorted()
          .toList();
      for (String name : names) {
        Class<?> type;
        try {
          type = Class.forName(name, false, React.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError unavailable) {
          continue;
        }
        if (!Registered.class.isAssignableFrom(type) || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
          continue;
        }
        types.add(type.asSubclass(Registered.class));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    types.sort(Comparator.comparing(Class::getName));
    return types;
  }

  static boolean hasPublicNoArgConstructor(Class<?> type) {
    try {
      return Modifier.isPublic(type.getModifiers()) && Modifier.isPublic(type.getConstructor().getModifiers());
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  static Built build(Class<? extends Registered> type) {
    Throwable failure = null;
    if (hasPublicNoArgConstructor(type)) {
      try {
        return new Built(type.getConstructor().newInstance(), Construction.PUBLIC_NO_ARG, null);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
        failure = e;
      }
    }
    try {
      Constructor<? extends Registered> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return new Built(constructor.newInstance(), Construction.HIDDEN_NO_ARG, failure);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      failure = failure == null ? e : failure;
    }
    try {
      return new Built(type.cast(unsafe().allocateInstance(type)), Construction.ALLOCATED, failure);
    } catch (InstantiationException | RuntimeException | LinkageError e) {
      return new Built(null, Construction.UNAVAILABLE, e);
    }
  }

  private static Unsafe unsafe() {
    try {
      Field field = Unsafe.class.getDeclaredField("theUnsafe");
      field.setAccessible(true);
      return (Unsafe) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
