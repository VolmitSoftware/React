package art.arcane.react.core.pluginapi;

import me.clip.placeholderapi.PlaceholderAPI;

import java.util.regex.Pattern;

public final class PluginApiPlaceholderReader {
  private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)§[0-9A-FK-ORX]");
  private static final Pattern NUMBER = Pattern.compile("[-+]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)");

  private PluginApiPlaceholderReader() {
  }

  public static double read(String placeholder) {
    String resolved = PlaceholderAPI.setPlaceholders(null, placeholder);
    return parseResolved(resolved, placeholder);
  }

  static double parseResolved(String resolved, String placeholder) {
    if (resolved == null || resolved.equals(placeholder) || resolved.length() > 256) {
      return Double.NaN;
    }
    String normalized = LEGACY_COLOR.matcher(resolved).replaceAll("").strip();
    if (normalized.startsWith("*")) {
      normalized = normalized.substring(1).stripLeading();
    }
    if (normalized.endsWith("%")) {
      normalized = normalized.substring(0, normalized.length() - 1).stripTrailing();
    }
    if (!NUMBER.matcher(normalized).matches()) {
      return Double.NaN;
    }
    double value = Double.parseDouble(normalized);
    return Double.isFinite(value) ? value : Double.NaN;
  }
}
