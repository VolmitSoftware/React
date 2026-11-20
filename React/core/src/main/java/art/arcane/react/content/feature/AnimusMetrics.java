package art.arcane.react.content.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Animus's network and subsystem-cost metrics, read from the {@code animus.*} values it publishes to React.
 */
public final class AnimusMetrics {
  public static final String SOURCE_ID = "animus";
  public static final String NET_BYTES_PER_SECOND = "animus.net.bytes_per_second";
  public static final String NET_PACKETS_PER_SECOND = "animus.net.packets_per_second";
  public static final String NET_PLAYER_PEAK_BYTES_PER_SECOND = "animus.net.player_peak.bytes_per_second";
  public static final String NET_KIND_PREFIX = "animus.net.kind.";
  public static final String NET_KIND_SUFFIX = ".bytes_per_second";
  public static final String COST_PREFIX = "animus.cost.";
  public static final String COST_SUFFIX = "_ms";
  public static final String COST_TOTAL_NAME = "total";
  public static final String PRESSURE_PREFIX = "animus.pressure.";

  private AnimusMetrics() {
  }

  /**
   * @param declared true once Animus declared its metrics, even before any reading arrived
   * @param readings fresh values by metric key
   */
  public static Snapshot parse(boolean declared, Map<String, Double> readings) {
    if (!declared) {
      return Snapshot.empty();
    }

    List<Entry> costs = new ArrayList<>();
    List<Entry> packetKinds = new ArrayList<>();
    double bytesPerSecond = Double.NaN;
    double packetsPerSecond = Double.NaN;
    double playerPeakBytesPerSecond = Double.NaN;
    double peakPressure = Double.NaN;

    for (Map.Entry<String, Double> reading : readings.entrySet()) {
      String key = reading.getKey();
      Double raw = reading.getValue();
      if (key == null || raw == null || !Double.isFinite(raw)) {
        continue;
      }

      double value = Math.max(0D, raw);
      switch (key) {
        case NET_BYTES_PER_SECOND -> bytesPerSecond = value;
        case NET_PACKETS_PER_SECOND -> packetsPerSecond = value;
        case NET_PLAYER_PEAK_BYTES_PER_SECOND -> playerPeakBytesPerSecond = value;
        default -> {
          String costName = segment(key, COST_PREFIX, COST_SUFFIX);
          if (costName != null) {
            if (!COST_TOTAL_NAME.equals(costName) && value > 0D) {
              costs.add(new Entry(costName, displayName(costName), value));
            }
            continue;
          }

          String kindName = segment(key, NET_KIND_PREFIX, NET_KIND_SUFFIX);
          if (kindName != null) {
            if (value > 0D) {
              packetKinds.add(new Entry(kindName, displayName(kindName), value));
            }
            continue;
          }

          if (segment(key, PRESSURE_PREFIX, "") != null) {
            double clamped = Math.min(100D, value);
            peakPressure = Double.isNaN(peakPressure) ? clamped : Math.max(peakPressure, clamped);
          }
        }
      }
    }

    costs.sort(ENTRY_ORDER);
    packetKinds.sort(ENTRY_ORDER);
    return new Snapshot(
        true,
        List.copyOf(costs),
        List.copyOf(packetKinds),
        bytesPerSecond,
        packetsPerSecond,
        playerPeakBytesPerSecond,
        peakPressure
    );
  }

  private static final Comparator<Entry> ENTRY_ORDER = Comparator
      .comparingDouble(Entry::value).reversed()
      .thenComparing(Entry::id);

  private static String segment(String key, String prefix, String suffix) {
    if (key == null || !key.startsWith(prefix) || !key.endsWith(suffix)) {
      return null;
    }

    int end = key.length() - suffix.length();
    if (end <= prefix.length()) {
      return null;
    }

    String name = key.substring(prefix.length(), end);
    return name.indexOf('.') >= 0 ? null : name;
  }

  public static String displayName(String id) {
    if (id == null || id.isBlank()) {
      return "";
    }

    String[] words = id.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
    StringBuilder display = new StringBuilder(id.length());
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }
      if (!display.isEmpty()) {
        display.append(' ');
      }
      display.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
      display.append(word.substring(1));
    }
    return display.isEmpty() ? id : display.toString();
  }

  public record Entry(String id, String displayName, double value) {
  }

  public record Snapshot(
      boolean declared,
      List<Entry> costs,
      List<Entry> packetKinds,
      double bytesPerSecond,
      double packetsPerSecond,
      double playerPeakBytesPerSecond,
      double peakPressure
  ) {
    public static Snapshot empty() {
      return new Snapshot(false, List.of(), List.of(), Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    public double totalCostMs() {
      double total = 0D;
      for (Entry cost : costs) {
        total += cost.value();
      }
      return total;
    }
  }
}
