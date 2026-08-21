package art.arcane.react.api.metric;

import org.bukkit.Material;

import java.util.Objects;

public record ReactMetric(String key, ReactMetricKind kind, String unit, String displayName, Material icon,
                          int decimals) {
  public ReactMetric {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(kind, "kind");
    unit = unit == null ? "" : unit.strip();
    displayName = displayName == null || displayName.isBlank() ? key : displayName.strip();
    icon = icon == null ? Material.SLIME_BALL : icon;
    decimals = Math.max(0, Math.min(4, decimals));
  }

  public static ReactMetric gauge(String key, String displayName, String unit) {
    return new ReactMetric(key, ReactMetricKind.GAUGE, unit, displayName, Material.SLIME_BALL, 0);
  }

  public static ReactMetric counter(String key, String displayName, String unit) {
    return new ReactMetric(key, ReactMetricKind.COUNTER, unit, displayName, Material.SLIME_BALL, 0);
  }

  public static ReactMetric rate(String key, String displayName, String unit) {
    return new ReactMetric(key, ReactMetricKind.RATE, unit, displayName, Material.SLIME_BALL, 1);
  }

  public static ReactMetric percent(String key, String displayName) {
    return new ReactMetric(key, ReactMetricKind.PERCENT, "%", displayName, Material.SLIME_BALL, 1);
  }

  public static ReactMetric millis(String key, String displayName) {
    return new ReactMetric(key, ReactMetricKind.MILLIS, "ms", displayName, Material.SLIME_BALL, 2);
  }

  public ReactMetric withIcon(Material replacement) {
    return new ReactMetric(key, kind, unit, displayName, replacement, decimals);
  }

  public ReactMetric withDecimals(int places) {
    return new ReactMetric(key, kind, unit, displayName, icon, places);
  }

  public ReactMetric withDisplayName(String replacement) {
    return new ReactMetric(key, kind, unit, replacement, icon, decimals);
  }

  public ReactMetric withUnit(String replacement) {
    return new ReactMetric(key, kind, replacement, displayName, icon, decimals);
  }
}
