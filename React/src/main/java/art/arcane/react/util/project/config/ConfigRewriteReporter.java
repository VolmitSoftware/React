package art.arcane.react.util.project.config;

import art.arcane.react.React;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConfigRewriteReporter {
  private static final int MAX_KEYS_PER_CATEGORY = 8;

  public static void reportRewrite(File file, String source, String beforeRaw, String afterRaw, Class<?> type) {
    String before = normalize(beforeRaw);
    String after = normalize(afterRaw);
    if (Objects.equals(before, after)) {
      return;
    }

    List<ConfigHotloadEngine.DiffEntry> changes = ConfigHotloadEngine.computeStructuredDiff(
        before, after, raw -> ConfigFileSupport.parseToJsonElement(raw, file), type);
    String path = relativize(file);
    String sourceTag = source == null || source.isBlank() ? "config" : source;

    if (changes.isEmpty()) {
      React.verbose("Canonicalized " + sourceTag + " [" + path + "] (format/order only).");
      return;
    }

    int removed = 0;
    int added = 0;
    int changed = 0;
    List<String> removedKeys = new ArrayList<>();
    List<String> addedKeys = new ArrayList<>();
    List<String> changedKeys = new ArrayList<>();
    for (ConfigHotloadEngine.DiffEntry changeEntry : changes) {
      if (ConfigHotloadEngine.REMOVED.equals(changeEntry.newValue())) {
        removed++;
        removedKeys.add(changeEntry.key());
      } else if (ConfigHotloadEngine.MISSING.equals(changeEntry.oldValue())) {
        added++;
        addedKeys.add(changeEntry.key());
      } else {
        changed++;
        changedKeys.add(changeEntry.key());
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

}
