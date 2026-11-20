package art.arcane.react.api.metric;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One metric a mod publishes to React. The same as a Bukkit plugin's {@code ReactMetric}, with the icon given as an
 * item id such as {@code minecraft:slime_ball}.
 */
public record ReactModMetric(String key, ReactMetricKind kind, String unit, String displayName, String icon,
                             int decimals) {
  public static final String DEFAULT_ICON = "minecraft:slime_ball";
  private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

  public ReactModMetric {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(kind, "kind");
    unit = unit == null ? "" : unit.strip();
    displayName = displayName == null || displayName.isBlank() ? key : displayName.strip();
    icon = itemId(icon);
    decimals = Math.max(0, Math.min(4, decimals));
  }

  public static ReactModMetric gauge(String key, String displayName, String unit) {
    return new ReactModMetric(key, ReactMetricKind.GAUGE, unit, displayName, DEFAULT_ICON, 0);
  }

  public static ReactModMetric counter(String key, String displayName, String unit) {
    return new ReactModMetric(key, ReactMetricKind.COUNTER, unit, displayName, DEFAULT_ICON, 0);
  }

  public static ReactModMetric rate(String key, String displayName, String unit) {
    return new ReactModMetric(key, ReactMetricKind.RATE, unit, displayName, DEFAULT_ICON, 1);
  }

  public static ReactModMetric percent(String key, String displayName) {
    return new ReactModMetric(key, ReactMetricKind.PERCENT, "%", displayName, DEFAULT_ICON, 1);
  }

  public static ReactModMetric millis(String key, String displayName) {
    return new ReactModMetric(key, ReactMetricKind.MILLIS, "ms", displayName, DEFAULT_ICON, 2);
  }

  public ReactModMetric withIcon(String replacement) {
    return new ReactModMetric(key, kind, unit, displayName, replacement, decimals);
  }

  public ReactModMetric withDecimals(int places) {
    return new ReactModMetric(key, kind, unit, displayName, icon, places);
  }

  public ReactModMetric withDisplayName(String replacement) {
    return new ReactModMetric(key, kind, unit, replacement, icon, decimals);
  }

  public ReactModMetric withUnit(String replacement) {
    return new ReactModMetric(key, kind, replacement, displayName, icon, decimals);
  }

  private static String itemId(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_ICON;
    }
    String id = raw.strip().toLowerCase(Locale.ROOT);
    if (id.indexOf(':') < 0) {
      id = "minecraft:" + id;
    }
    return ITEM_ID.matcher(id).matches() ? id : DEFAULT_ICON;
  }
}
