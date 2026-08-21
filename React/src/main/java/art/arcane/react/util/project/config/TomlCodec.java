package art.arcane.react.util.project.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TomlCodec {
  private static final Gson JSON = new Gson();

  private TomlCodec() {
  }

  public static <T> T fromToml(String raw, Class<T> type) throws IOException {
    try {
      Object parsed = parseToml(raw);
      String json = JSON.toJson(parsed);
      return JSON.fromJson(json, type);
    } catch (Throwable e) {
      throw new IOException("Invalid toml", e);
    }
  }

  public static JsonElement toJsonElement(String raw) throws IOException {
    try {
      Object parsed = parseToml(raw);
      String json = JSON.toJson(parsed);
      return JSON.fromJson(json, JsonElement.class);
    } catch (Throwable e) {
      throw new IOException("Invalid toml", e);
    }
  }

  public static String toToml(Object object, String sourceTag) {
    return new ReflectiveTomlWriter(sourceTag).write(object);
  }

  public static String toToml(JsonElement element) {
    Object data = JSON.fromJson(element, Object.class);
    return new GenericTomlWriter().write(data);
  }

  private static Object parseToml(String raw) {
    Toml toml = new Toml().read(raw == null ? "" : raw);
    Map<String, Object> map = toml.toMap();
    if (map == null) {
      return new LinkedHashMap<String, Object>();
    }
    return map;
  }

  private static List<Field> getSerializableFields(Class<?> type) {
    List<Field> out = new ArrayList<>();
    collectFields(type, out);
    return out;
  }

  private static void collectFields(Class<?> type, List<Field> out) {
    if (type == null || type == Object.class) {
      return;
    }

    collectFields(type.getSuperclass(), out);
    for (Field field : type.getDeclaredFields()) {
      if (field.isSynthetic()) {
        continue;
      }
      int modifiers = field.getModifiers();
      if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
        continue;
      }
      try {
        field.setAccessible(true);
        out.add(field);
      } catch (Throwable ignored) {
        // Strongly encapsulated JDK internals are intentionally skipped.
      }
    }
  }

  private static Object getFieldValue(Field field, Object object) {
    try {
      return field.get(object);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static boolean isInlineValue(Object value) {
    if (value == null) {
      return true;
    }

    if (value instanceof String
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Character
        || value instanceof Enum<?>
        || value instanceof UUID
        || value instanceof java.time.temporal.TemporalAccessor) {
      return true;
    }

    if (value instanceof Map<?, ?>) {
      return false;
    }

    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        Object v = Array.get(value, i);
        if (!isInlineValue(v) || v instanceof Map<?, ?> || v instanceof Collection<?>) {
          return false;
        }
      }
      return true;
    }

    if (value instanceof Collection<?> collection) {
      for (Object item : collection) {
        if (!isInlineValue(item) || item instanceof Map<?, ?> || item instanceof Collection<?>) {
          return false;
        }
      }
      return true;
    }

    String className = value.getClass().getName();
    if (className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("jdk.")) {
      return true;
    }

    return false;
  }

  private static String formatInlineValue(Object value) {
    if (value == null) {
      return "\"\"";
    }

    if (value instanceof String string) {
      return '"' + escape(string) + '"';
    }
    if (value instanceof Character c) {
      return '"' + escape(String.valueOf(c)) + '"';
    }
    if (value instanceof Enum<?> enumValue) {
      return '"' + escape(enumValue.name()) + '"';
    }
    if (value instanceof Number || value instanceof Boolean) {
      return String.valueOf(value);
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      List<String> parts = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        parts.add(formatInlineValue(Array.get(value, i)));
      }
      return "[" + String.join(", ", parts) + "]";
    }
    if (value instanceof Collection<?> collection) {
      List<String> parts = new ArrayList<>(collection.size());
      for (Object item : collection) {
        parts.add(formatInlineValue(item));
      }
      return "[" + String.join(", ", parts) + "]";
    }

    return '"' + escape(String.valueOf(value)) + '"';
  }

  private static String renderPath(String path) {
    if (path == null || path.isBlank()) {
      return "";
    }

    String[] parts = path.split("\\.");
    List<String> rendered = new ArrayList<>(parts.length);
    for (String part : parts) {
      rendered.add(formatKey(part));
    }
    return String.join(".", rendered);
  }

  private static String formatKey(String key) {
    if (key == null || key.isBlank()) {
      return "\"\"";
    }

    if (key.matches("[A-Za-z0-9_-]+")) {
      return key;
    }
    return '"' + escape(key) + '"';
  }

  private static String joinPath(String a, String b) {
    if (a == null || a.isBlank()) {
      return b;
    }
    if (b == null || b.isBlank()) {
      return a;
    }
    return a + "." + b;
  }

  private static String parentPath(String path) {
    if (path == null || path.isBlank()) {
      return "";
    }

    int dot = path.lastIndexOf('.');
    if (dot <= 0) {
      return "";
    }

    return path.substring(0, dot);
  }

  private static Collection<?> asCollection(Object value) {
    if (value instanceof Collection<?> collection) {
      return collection;
    }

    if (value == null || !value.getClass().isArray()) {
      return List.of();
    }

    int length = Array.getLength(value);
    List<Object> out = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      out.add(Array.get(value, i));
    }
    return out;
  }

  private static boolean isPojoSectionObject(Object value) {
    if (value == null) {
      return false;
    }

    if (isInlineValue(value) || value instanceof Map<?, ?> || value instanceof Collection<?> || value.getClass().isArray()) {
      return false;
    }

    String className = value.getClass().getName();
    if (className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("jdk.")) {
      return false;
    }

    return true;
  }

  private static String escape(String input) {
    return input
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private static String normalize(String raw) {
    if (raw == null) {
      return "";
    }

    String normalized = raw.replace("\r\n", "\n").stripTrailing();
    if (!normalized.isEmpty()) {
      normalized += "\n";
    }
    return normalized;
  }

  private static final class ReflectiveTomlWriter {
    private final StringBuilder out = new StringBuilder();
    private final String sourceTag;

    private ReflectiveTomlWriter(String sourceTag) {
      this.sourceTag = sourceTag == null ? "config" : sourceTag;
    }

    private String write(Object root) {
      if (root == null) {
        return "";
      }

      out.append("# React configuration - ").append(sourceTag).append('\n');
      out.append("# This file is canonicalized on load. Comments and key order may refresh automatically.\n");
      out.append("# Docs schema: 2\n");
      String localizedDescription = ConfigLocalization.description(root.getClass());
      String rootDescription = localizedDescription.isBlank()
          ? ConfigDocumentation.buildRootDescription(sourceTag, root.getClass())
          : localizedDescription;
      if (rootDescription != null && !rootDescription.isBlank()) {
        out.append("#\n");
        out.append("# ").append(rootDescription).append('\n');
      }
      out.append('\n');
      writePojoSection("", root);
      return normalize(out.toString());
    }

    private void writePojoSection(String path, Object sectionObject) {
      if (sectionObject == null) {
        return;
      }

      List<Field> fields = getSerializableFields(sectionObject.getClass());
      List<Field> deferred = new ArrayList<>();

      for (Field field : fields) {
        Object value = getFieldValue(field, sectionObject);
        if (value == null) {
          continue;
        }
        if (!ConfigDocumentation.shouldExposeField(sourceTag, path, field, value)) {
          continue;
        }

        if (isInlineValue(value)) {
          writeFieldComments(path, field, value);
          out.append(formatKey(field.getName())).append(" = ").append(formatInlineValue(value)).append('\n');
        } else {
          deferred.add(field);
        }
      }

      for (Field field : deferred) {
        Object value = getFieldValue(field, sectionObject);
        if (value == null) {
          continue;
        }
        if (!ConfigDocumentation.shouldExposeField(sourceTag, path, field, value)) {
          continue;
        }

        String childPath = joinPath(path, field.getName());
        if (value instanceof Map<?, ?> map) {
          writeMapSection(childPath, map, field);
          continue;
        }

        if (value instanceof Collection<?> collection) {
          writeCollectionSection(childPath, collection, field);
          continue;
        }

        if (value.getClass().isArray()) {
          writeCollectionSection(childPath, asCollection(value), field);
          continue;
        }

        if (!isPojoSectionObject(value)) {
          writeFieldComments(path, field, value);
          out.append(formatKey(field.getName()))
              .append(" = ")
              .append(formatInlineValue(value))
              .append('\n');
          continue;
        }

        writeSectionHeader(childPath, ConfigDocumentation.buildSectionComments(sourceTag, childPath));
        writePojoSection(childPath, value);
      }
    }

    private void writeMapSection(String sectionPath, Map<?, ?> map, Field sourceField) {
      writeSectionHeader(sectionPath, ConfigDocumentation.buildFieldComments(sourceTag, sectionPath, sourceField, map));
      if (map.isEmpty()) {
        return;
      }

      List<Map.Entry<?, ?>> deferred = new ArrayList<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry == null || entry.getKey() == null) {
          continue;
        }

        Object value = entry.getValue();
        if (value == null) {
          continue;
        }

        if (isInlineValue(value)) {
          out.append(formatKey(String.valueOf(entry.getKey())))
              .append(" = ")
              .append(formatInlineValue(value))
              .append('\n');
        } else {
          deferred.add(entry);
        }
      }

      for (Map.Entry<?, ?> entry : deferred) {
        Object value = entry.getValue();
        if (value == null || entry.getKey() == null) {
          continue;
        }

        String childPath = joinPath(sectionPath, String.valueOf(entry.getKey()));
        if (value instanceof Map<?, ?> nested) {
          writeSectionHeader(childPath, List.of());
          writeMapBody(childPath, nested);
        } else if (value instanceof Collection<?> collection) {
          writeCollectionSection(childPath, collection, null);
        } else if (value.getClass().isArray()) {
          writeCollectionSection(childPath, asCollection(value), null);
        } else {
          writeSectionHeader(childPath, List.of());
          writePojoSection(childPath, value);
        }
      }
    }

    private void writeMapBody(String sectionPath, Map<?, ?> map) {
      List<Map.Entry<?, ?>> deferred = new ArrayList<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry == null || entry.getKey() == null || entry.getValue() == null) {
          continue;
        }
        if (isInlineValue(entry.getValue())) {
          out.append(formatKey(String.valueOf(entry.getKey())))
              .append(" = ")
              .append(formatInlineValue(entry.getValue()))
              .append('\n');
        } else {
          deferred.add(entry);
        }
      }

      for (Map.Entry<?, ?> entry : deferred) {
        String childPath = joinPath(sectionPath, String.valueOf(entry.getKey()));
        Object value = entry.getValue();
        if (value instanceof Map<?, ?> nested) {
          writeSectionHeader(childPath, List.of());
          writeMapBody(childPath, nested);
        } else if (value instanceof Collection<?> collection) {
          writeCollectionSection(childPath, collection, null);
        } else if (value.getClass().isArray()) {
          writeCollectionSection(childPath, asCollection(value), null);
        } else {
          writeSectionHeader(childPath, List.of());
          writePojoSection(childPath, value);
        }
      }
    }

    private void writeCollectionSection(String sectionPath, Collection<?> collection, Field sourceField) {
      Collection<?> safeCollection = collection == null ? List.of() : collection;
      if (safeCollection.isEmpty()) {
        if (sourceField != null) {
          writeFieldComments(parentPath(sectionPath), sourceField, safeCollection);
          out.append(formatKey(sourceField.getName())).append(" = []").append('\n');
        }
        return;
      }

      boolean complex = false;
      for (Object item : safeCollection) {
        if (item == null) {
          continue;
        }

        if (item instanceof Map<?, ?> || item instanceof Collection<?> || item.getClass().isArray() || !isInlineValue(item)) {
          complex = true;
          break;
        }
      }

      if (!complex && sourceField != null) {
        writeFieldComments(parentPath(sectionPath), sourceField, safeCollection);
        out.append(formatKey(sourceField.getName()))
            .append(" = ")
            .append(formatInlineValue(safeCollection))
            .append('\n');
        return;
      }

      List<String> comments = sourceField == null
          ? List.of()
          : ConfigDocumentation.buildFieldComments(sourceTag, sectionPath, sourceField, safeCollection);
      boolean wroteAny = false;

      for (Object item : safeCollection) {
        if (item == null) {
          continue;
        }

        writeArrayHeader(sectionPath, wroteAny ? List.of() : comments);
        wroteAny = true;

        if (item instanceof Map<?, ?> map) {
          writeMapBody(sectionPath, map);
          continue;
        }

        if (item instanceof Collection<?> nestedCollection) {
          out.append("value = ").append(formatInlineValue(nestedCollection)).append('\n');
          continue;
        }

        if (item.getClass().isArray()) {
          out.append("value = ").append(formatInlineValue(item)).append('\n');
          continue;
        }

        if (isInlineValue(item) || !isPojoSectionObject(item)) {
          out.append("value = ").append(formatInlineValue(item)).append('\n');
          continue;
        }

        writePojoSection(sectionPath, item);
      }

      if (!wroteAny && sourceField != null) {
        writeFieldComments(parentPath(sectionPath), sourceField, safeCollection);
        out.append(formatKey(sourceField.getName())).append(" = []").append('\n');
      }
    }

    private void writeArrayHeader(String path, List<String> comments) {
      if (path == null || path.isBlank()) {
        return;
      }

      if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
        out.append('\n');
      }
      if (!out.isEmpty()) {
        out.append('\n');
      }

      for (String comment : comments) {
        if (comment == null || comment.isBlank()) {
          continue;
        }
        out.append("# ").append(comment.strip()).append('\n');
      }

      out.append("[[").append(renderPath(path)).append("]]").append('\n');
    }

    private void writeFieldComments(String path, Field field, Object value) {
      List<String> comments = ConfigDocumentation.buildFieldComments(sourceTag, path, field, value);
      for (String comment : comments) {
        if (comment == null || comment.isBlank()) {
          continue;
        }
        out.append("# ").append(comment.strip()).append('\n');
      }
    }

    private void writeSectionHeader(String path, List<String> comments) {
      if (path == null || path.isBlank()) {
        return;
      }

      if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
        out.append('\n');
      }
      if (!out.isEmpty()) {
        out.append('\n');
      }

      for (String comment : comments) {
        if (comment == null || comment.isBlank()) {
          continue;
        }
        out.append("# ").append(comment.strip()).append('\n');
      }
      out.append('[').append(renderPath(path)).append(']').append('\n');
    }
  }

  private static final class GenericTomlWriter {
    private final StringBuilder out = new StringBuilder();
    private String lastTopLevelSection;

    private String write(Object root) {
      if (root instanceof Map<?, ?> map) {
        writeMapSection("", map, 0);
        return normalize(out.toString());
      }

      out.append("value = ").append(formatInlineValue(root)).append('\n');
      return normalize(out.toString());
    }

    private void writeMapSection(String path, Map<?, ?> map, int depth) {
      if (!path.isBlank()) {
        writeSectionHeader(path, depth);
      }

      List<Map.Entry<?, ?>> deferred = new ArrayList<>();
      String valueIndent = "  ".repeat(Math.max(0, depth));
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry == null || entry.getKey() == null || entry.getValue() == null) {
          continue;
        }

        Object value = entry.getValue();
        if (isInlineValue(value)) {
          out.append(valueIndent)
              .append(formatKey(String.valueOf(entry.getKey())))
              .append(" = ")
              .append(formatInlineValue(value))
              .append('\n');
        } else {
          deferred.add(entry);
        }
      }

      for (Map.Entry<?, ?> entry : deferred) {
        String childPath = joinPath(path, String.valueOf(entry.getKey()));
        Object value = entry.getValue();
        if (value instanceof Map<?, ?> nested) {
          writeMapSection(childPath, nested, depth + 1);
        } else {
          Map<String, Object> wrapper = new LinkedHashMap<>();
          wrapper.put("value", value);
          writeMapSection(childPath, wrapper, depth + 1);
        }
      }
    }

    private void writeSectionHeader(String path, int depth) {
      String topLevel = topLevelSegment(path);
      if (depth == 1 && (lastTopLevelSection == null || !lastTopLevelSection.equals(topLevel))) {
        if (!out.isEmpty()) {
          out.append('\n');
        }
        out.append("# ").append(topLevel).append('\n');
        lastTopLevelSection = topLevel;
      } else if (!out.isEmpty()) {
        out.append('\n');
      }

      out.append('[').append(renderPath(path)).append(']').append('\n');
    }

    private String topLevelSegment(String path) {
      if (path == null || path.isBlank()) {
        return "";
      }

      int dot = path.indexOf('.');
      if (dot == -1) {
        return path;
      }
      return path.substring(0, dot);
    }
  }
}
