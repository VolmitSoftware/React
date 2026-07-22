package art.arcane.react.util.project.config;

import art.arcane.react.localization.ReactMessages;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

public class ConfigLocalizationCatalogTest {
  private static final String KEY_PREFIX = "config.documentation.annotation.";

  @Test
  public void catalogContainsEveryConfigAnnotationWithExactEnglishDefault() throws Exception {
    Set<Class<?>> annotatedTypes = discoverAnnotatedTypes();
    Map<String, String> expected = expectedMessages(annotatedTypes);
    Map<String, String> actual = catalogMessages();

    Assertions.assertFalse(expected.isEmpty());
    Assertions.assertEquals(expected.keySet(), actual.keySet());
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void staticTypeIndexExactlyCoversAnnotationOwners() throws Exception {
    Set<String> catalogOwnerNames = new HashSet<>();
    for (Class<?> type : ConfigLocalization.catalogTypes()) {
      catalogOwnerNames.add(type.getName());
    }
    Assertions.assertEquals(discoverAnnotationOwnerNames(), catalogOwnerNames);
  }

  private static Set<Class<?>> discoverAnnotatedTypes() {
    Set<Class<?>> types = new HashSet<>();
    Set<Class<?>> visited = new HashSet<>();
    for (Class<?> type : ConfigLocalization.catalogTypes()) {
      addAnnotatedTypes(type, types, visited);
    }
    return types;
  }

  private static Set<String> discoverAnnotationOwnerNames() throws Exception {
    Path root = Path.of("src", "main", "java").toAbsolutePath().normalize();
    Assertions.assertTrue(Files.isDirectory(root), "Expected Java source directory at " + root);

    Set<String> owners = new HashSet<>();
    try (Stream<Path> paths = Files.walk(root.resolve("art/arcane/react"))) {
      List<Path> sourceFiles = paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .toList();
      for (Path sourceFile : sourceFiles) {
        String source = Files.readString(sourceFile);
        if (source.contains("ConfigDoc(") || source.contains("ConfigDescription(")) {
          owners.add(className(root, sourceFile));
        }
      }
    }
    return owners;
  }

  private static void addAnnotatedTypes(Class<?> type, Set<Class<?>> types, Set<Class<?>> visited) {
    if (type == null || !visited.add(type)) {
      return;
    }
    if (hasConfigAnnotation(type)) {
      types.add(type);
    }
    for (Class<?> nestedType : type.getDeclaredClasses()) {
      addAnnotatedTypes(nestedType, types, visited);
    }
  }

  private static boolean hasConfigAnnotation(Class<?> type) {
    if (type.getDeclaredAnnotation(ConfigDescription.class) != null) {
      return true;
    }
    for (Field field : type.getDeclaredFields()) {
      if (field.getDeclaredAnnotation(ConfigDoc.class) != null) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, String> expectedMessages(Set<Class<?>> types) {
    Map<String, String> expected = new TreeMap<>();
    for (Class<?> type : types) {
      ConfigDescription description = type.getDeclaredAnnotation(ConfigDescription.class);
      if (description != null && !description.value().isBlank()) {
        putUnique(expected, ConfigLocalization.descriptionId(type), description.value().strip());
      }
      for (Field field : type.getDeclaredFields()) {
        ConfigDoc annotation = field.getDeclaredAnnotation(ConfigDoc.class);
        if (annotation == null) {
          continue;
        }
        putUnique(expected, ConfigLocalization.fieldSummaryId(field), annotation.value().strip());
        if (!annotation.impact().isBlank()) {
          putUnique(expected, ConfigLocalization.fieldImpactId(field), annotation.impact().strip());
        }
      }
    }
    return expected;
  }

  private static Map<String, String> catalogMessages() {
    Map<String, String> actual = new TreeMap<>();
    for (MessageKey key : ReactMessages.catalog().keys()) {
      if (!key.id().startsWith(KEY_PREFIX)) {
        continue;
      }
      Assertions.assertInstanceOf(TextKey.class, key);
      TextKey textKey = (TextKey) key;
      putUnique(actual, textKey.id(), textKey.english());
    }
    return actual;
  }

  private static void putUnique(Map<String, String> messages, String id, String english) {
    String previous = messages.putIfAbsent(id, english);
    Assertions.assertNull(previous, "Duplicate config localization key " + id);
  }

  private static String className(Path root, Path sourceFile) {
    String relative = root.relativize(sourceFile).toString();
    return relative
        .substring(0, relative.length() - ".java".length())
        .replace('/', '.')
        .replace('\\', '.');
  }
}
