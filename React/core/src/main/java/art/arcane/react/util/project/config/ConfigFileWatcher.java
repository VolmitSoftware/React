package art.arcane.react.util.project.config;

import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;
import com.google.gson.JsonElement;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Finds operator edits to React's config files by polling. A change is reported once the file has kept the same size
 * and modification time for one more poll, and only when its text differs from what React last applied or wrote
 * itself, so React's own writes never come back as edits.
 */
public final class ConfigFileWatcher {
  public static final int MAX_NOTICES_PER_FILE = 12;
  private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;

  public record Change(File file, String before, String after) {
  }

  public record Notice(MessageKey key, MessageArgument[] arguments) {
  }

  private record State(long stamp, boolean settled) {
  }

  private final Supplier<List<File>> files;
  private final Map<File, String> applied = new ConcurrentHashMap<>();
  private final Map<File, State> states = new ConcurrentHashMap<>();

  public ConfigFileWatcher(Supplier<List<File>> files) {
    this.files = Objects.requireNonNull(files, "files");
  }

  /**
   * Takes the current text of every watched file as already applied.
   */
  public void prime() {
    for (File file : files.get()) {
      File key = key(file);
      states.put(key, new State(stamp(key), true));
      String content = read(key);
      if (content != null) {
        applied.put(key, normalize(content));
      }
    }
  }

  /**
   * Records text React wrote or applied itself.
   */
  public void accept(File file, String content) {
    if (file == null) {
      return;
    }
    File key = key(file);
    if (content == null) {
      applied.remove(key);
    } else {
      applied.put(key, normalize(content));
    }
  }

  public List<Change> poll() {
    List<Change> changes = new ArrayList<>();
    List<File> watched = new ArrayList<>();
    for (File file : files.get()) {
      watched.add(key(file));
    }
    for (File file : applied.keySet()) {
      if (!watched.contains(file)) {
        watched.add(file);
      }
    }
    for (File file : watched) {
      long stamp = stamp(file);
      State previous = states.get(file);
      if (previous == null || previous.stamp() != stamp) {
        states.put(file, new State(stamp, false));
        continue;
      }
      if (previous.settled()) {
        continue;
      }
      states.put(file, new State(stamp, true));
      String content = read(file);
      String after = content == null ? null : normalize(content);
      String before = applied.get(file);
      if (!Objects.equals(before, after)) {
        changes.add(new Change(file, before, after));
      }
    }
    return changes;
  }

  public void clear() {
    applied.clear();
    states.clear();
  }

  /**
   * The operator messages for a change: one line per changed key, then a count of the keys left out.
   */
  public static List<Notice> notices(String relativePath, String before, String after, Class<?> configType) {
    List<ConfigHotloadEngine.DiffEntry> diffs = ConfigHotloadEngine.computeStructuredDiff(
        before == null ? "" : before,
        after == null ? "" : after,
        ConfigFileWatcher::parse,
        configType
    );
    List<Notice> notices = new ArrayList<>();
    int shown = Math.min(MAX_NOTICES_PER_FILE, diffs.size());
    for (int index = 0; index < shown; index++) {
      ConfigHotloadEngine.DiffEntry diff = diffs.get(index);
      notices.add(new Notice(RuntimeMessages.HOTLOAD_DIFF, new MessageArgument[]{
          MessageArgument.untrusted("file", relativePath),
          MessageArgument.untrusted("key", diff.key()),
          MessageArgument.untrusted("before", formatValue(diff.oldValue())),
          MessageArgument.untrusted("after", formatValue(diff.newValue()))
      }));
    }
    if (diffs.size() > shown) {
      notices.add(new Notice(RuntimeMessages.HOTLOAD_TRUNCATED, new MessageArgument[]{
          MessageArgument.untrusted("count", diffs.size() - shown),
          MessageArgument.untrusted("file", relativePath)
      }));
    }
    return notices;
  }

  private static JsonElement parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return TomlCodec.toJsonElement(raw);
    } catch (IOException | RuntimeException failure) {
      return null;
    }
  }

  private static String formatValue(String value) {
    if (ConfigHotloadEngine.MISSING.equals(value)) {
      return ReactLanguage.plain(RuntimeMessages.HOTLOAD_MISSING);
    }
    if (ConfigHotloadEngine.REMOVED.equals(value)) {
      return ReactLanguage.plain(RuntimeMessages.HOTLOAD_REMOVED);
    }
    return ConfigHotloadEngine.compactValue(value, 120);
  }

  public static String normalize(String text) {
    return text.replace("\r\n", "\n").stripTrailing();
  }

  private static File key(File file) {
    return file.getAbsoluteFile().toPath().normalize().toFile();
  }

  private static long stamp(File file) {
    return file.isFile() ? file.lastModified() * 31L + file.length() : -1L;
  }

  private static String read(File file) {
    if (!file.isFile() || file.length() > MAX_FILE_BYTES) {
      return null;
    }
    try {
      return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      return null;
    }
  }
}
