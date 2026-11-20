package art.arcane.react.util.project.config;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ConfigLocalization {
  private static final String KEY_PREFIX = "config.documentation.annotation.";

  private ConfigLocalization() {
  }

  public static void addTo(MessageCatalog.Builder builder, Collection<Class<?>> types) {
    Set<Class<?>> visited = new HashSet<>();
    for (Class<?> type : types) {
      addType(builder, type, visited);
    }
  }

  public static String description(Class<?> type) {
    Class<?> cursor = type;
    while (cursor != null && cursor != Object.class) {
      ConfigDescription annotation = cursor.getDeclaredAnnotation(ConfigDescription.class);
      if (annotation != null) {
        String english = annotation.value().strip();
        return ReactLanguage.raw(descriptionId(cursor), english);
      }
      cursor = cursor.getSuperclass();
    }
    return "";
  }

  public static String fieldSummary(Field field) {
    if (field == null) {
      return "";
    }
    ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);
    if (annotation == null) {
      return "";
    }
    String english = annotation.value().strip();
    return ReactLanguage.raw(fieldSummaryId(field), english);
  }

  public static String fieldImpact(Field field) {
    if (field == null) {
      return "";
    }
    ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);
    if (annotation == null || annotation.impact().isBlank()) {
      return "";
    }
    String english = annotation.impact().strip();
    return ReactLanguage.raw(fieldImpactId(field), english);
  }

  public static String descriptionId(Class<?> type) {
    return KEY_PREFIX + typeId(type) + ".description";
  }

  public static String fieldSummaryId(Field field) {
    return KEY_PREFIX + typeId(field.getDeclaringClass()) + ".field." + normalize(field.getName()) + ".summary";
  }

  public static String fieldImpactId(Field field) {
    return KEY_PREFIX + typeId(field.getDeclaringClass()) + ".field." + normalize(field.getName()) + ".impact";
  }

  private static void addType(MessageCatalog.Builder builder, Class<?> type, Set<Class<?>> visited) {
    if (type == null || !visited.add(type)) {
      return;
    }

    ConfigDescription description = type.getDeclaredAnnotation(ConfigDescription.class);
    if (description != null && !description.value().isBlank()) {
      builder.add(TextKey.of(descriptionId(type), description.value().strip()));
    }

    Field[] fields = type.getDeclaredFields();
    Arrays.sort(fields, Comparator.comparing(Field::getName));
    for (Field field : fields) {
      ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);
      if (annotation == null) {
        continue;
      }
      builder.add(TextKey.of(fieldSummaryId(field), annotation.value().strip()));
      if (!annotation.impact().isBlank()) {
        builder.add(TextKey.of(fieldImpactId(field), annotation.impact().strip()));
      }
    }

    Class<?>[] nestedTypes = type.getDeclaredClasses();
    Arrays.sort(nestedTypes, Comparator.comparing(Class::getName));
    for (Class<?> nestedType : nestedTypes) {
      addType(builder, nestedType, visited);
    }
  }

  private static String typeId(Class<?> type) {
    return normalize(type == null ? "unknown" : type.getName());
  }

  private static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replace('$', '_').replaceAll("[^a-z0-9_.-]", "_");
  }
}
