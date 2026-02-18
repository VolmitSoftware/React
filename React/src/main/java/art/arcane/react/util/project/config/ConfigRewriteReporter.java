package art.arcane.react.util.config;

import art.arcane.react.React;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.*;

public class ConfigRewriteReporter {
  private static final int MAX_KEYS_PER_CATEGORY = 8;
  private static final String MISSING = "<missing>";
  private static final String REMOVED = "<removed>";

  public static void reportRewrite(File file, String source, String beforeRaw, String afterRaw) {
    String before = normalize(beforeRaw);
    String after = normalize(afterRaw);
    if (Objects.equals(before, after)) {
      return;
    }

    List<Change> changes = computeDiff(before, after);
    String path = relativize(file);
    String sourceTag = source == null || source.isBlank() ? "config" : source;

    if (changes.isEmpty()) {
      React.info("Canonicalized " + sourceTag + " [" + path + "] (format/order only).");
      return;
    }

    int removed = 0;
    int added = 0;
    int changed = 0;
    List<String> removedKeys = new ArrayList<>();
    List<String> addedKeys = new ArrayList<>();
    List<String> changedKeys = new ArrayList<>();
    for (Change changeEntry : changes) {
      if (changeEntry.type == ChangeType.REMOVED) {
        removed++;
        removedKeys.add(changeEntry.key);
      } else if (changeEntry.type == ChangeType.ADDED) {
        added++;
        addedKeys.add(changeEntry.key);
      } else {
        changed++;
        changedKeys.add(changeEntry.key);
      }
    }

    React.warn("Canonicalized " + sourceTag + " [" + path + "] with schema changes (removed=" + removed + ", added=" + added + ", changed=" + changed + ").");
    if (!removedKeys.isEmpty()) {
      React.warn(" - removed keys: " + summarizeKeys(removedKeys));
    }
    if (!addedKeys.isEmpty()) {
      React.info(" - added keys: " + summarizeKeys(addedKeys));
    }
    if (!changedKeys.isEmpty()) {
      React.info(" - changed keys: " + summarizeKeys(changedKeys));
    }
  }

  public static void reportFallbackRewrite(File file, String source, String reason) {
    String path = relativize(file);
    String sourceTag = source == null || source.isBlank() ? "config" : source;
    String reasonText = reason == null || reason.isBlank() ? "invalid/unsupported content" : reason;
    React.warn("Rewrote " + sourceTag + " [" + path + "] using fallback defaults (" + reasonText + ").");
  }

  private static List<Change> computeDiff(String before, String after) {
    Map<String, String> left = flattenForDiff(before);
    Map<String, String> right = flattenForDiff(after);
    Set<String> keys = new HashSet<>(left.keySet());
    keys.addAll(right.keySet());

    List<String> ordered = new ArrayList<>(keys);
    ordered.sort(String::compareTo);

    List<Change> out = new ArrayList<>();
    for (String key : ordered) {
      boolean inLeft = left.containsKey(key);
      boolean inRight = right.containsKey(key);
      String oldValue = inLeft ? left.get(key) : MISSING;
      String newValue = inRight ? right.get(key) : REMOVED;
      if (Objects.equals(oldValue, newValue)) {
        continue;
      }

      ChangeType type;
      if (inLeft && !inRight) {
        type = ChangeType.REMOVED;
      } else if (!inLeft && inRight) {
        type = ChangeType.ADDED;
      } else {
        type = ChangeType.CHANGED;
      }

      out.add(new Change(type, key));
    }

    return out;
  }

  private static Map<String, String> flattenForDiff(String raw) {
    JsonElement element = parseStructured(raw);
    if (element == null) {
      Map<String, String> fallback = new HashMap<>();
      if (raw != null && !raw.isBlank()) {
        fallback.put("$", normalize(raw));
      }
      return fallback;
    }

    Map<String, String> out = new HashMap<>();
    flattenJson("$", element, out);
    return out;
  }

  private static void flattenJson(String path, JsonElement element, Map<String, String> out) {
    if (element == null || element.isJsonNull()) {
      out.put(path, "null");
      return;
    }

    if (element.isJsonPrimitive()) {
      out.put(path, element.toString());
      return;
    }

    if (element.isJsonArray()) {
      if (element.getAsJsonArray().size() == 0) {
        out.put(path, "[]");
        return;
      }

      for (int i = 0; i < element.getAsJsonArray().size(); i++) {
        flattenJson(path + "[" + i + "]", element.getAsJsonArray().get(i), out);
      }
      return;
    }

    JsonObject object = element.getAsJsonObject();
    if (object.entrySet().isEmpty()) {
      out.put(path, "{}");
      return;
    }

    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      flattenJson(path + "." + entry.getKey(), entry.getValue(), out);
    }
  }

  private static JsonElement parseStructured(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    return ConfigFileSupport.parseToJsonElement(raw, null);
  }

  private static String summarizeKeys(List<String> keys) {
    if (keys == null || keys.isEmpty()) {
      return "(none)";
    }

    int shown = Math.min(MAX_KEYS_PER_CATEGORY, keys.size());
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < shown; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(keys.get(i));
    }

    if (keys.size() > shown) {
      sb.append(" (+").append(keys.size() - shown).append(" more)");
    }

    return sb.toString();
  }

  private static String normalize(String json) {
    if (json == null) {
      return null;
    }
    return ConfigFileSupport.normalize(json);
  }

  private static String relativize(File file) {
    if (file == null) {
      return "<unknown>";
    }

    try {
      File dataFolder = React.instance == null ? null : React.instance.getDataFolder();
      if (dataFolder == null) {
        return file.getPath();
      }
      return dataFolder.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    } catch (Throwable ignored) {
      return file.getPath();
    }
  }

  private enum ChangeType {
    REMOVED,
    ADDED,
    CHANGED
  }

  private static class Change {
    private final ChangeType type;
    private final String key;

    private Change(ChangeType type, String key) {
      this.type = type;
      this.key = key;
    }
  }
}
