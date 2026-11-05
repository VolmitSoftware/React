package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.ThirdPartyMetricRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reads the metrics Animus publishes through the public {@code ReactMetricSource} API. Keys are
 * discovered by prefix so Animus decides which subsystems and packet kinds fit inside the
 * per-source metric budget.
 */
final class AnimusMetricSeries {
  static final String SOURCE_ID = "animus";
  static final String NET_BYTES_PER_SECOND = "animus.net.bytes_per_second";
  static final String NET_PACKETS_PER_SECOND = "animus.net.packets_per_second";
  static final String NET_PLAYER_PEAK_BYTES_PER_SECOND = "animus.net.player_peak.bytes_per_second";
  static final String NET_KIND_PREFIX = "animus.net.kind.";
  static final String NET_KIND_SUFFIX = ".bytes_per_second";
  static final String COST_PREFIX = "animus.cost.";
  static final String COST_SUFFIX = "_ms";
  static final String COST_TOTAL_NAME = "total";
  static final String PRESSURE_PREFIX = "animus.pressure.";
  private static final long CACHE_TTL_MS = 250L;
  private static volatile CachedSnapshot cached = new CachedSnapshot(Snapshot.empty(), 0L);

  private AnimusMetricSeries() {
  }

  static Snapshot snapshot() {
    long now = System.currentTimeMillis();
    CachedSnapshot current = cached;
    if (now >= current.capturedAtMs() && now - current.capturedAtMs() < CACHE_TTL_MS) {
      return current.snapshot();
    }

    IntegrationController controller = React.controller(IntegrationController.class);
    ThirdPartyMetricRegistry registry = controller == null ? null : controller.getThirdPartyMetrics();
    Snapshot snapshot = fromStore(registry == null ? null : registry.store(), now);
    cached = new CachedSnapshot(snapshot, now);
    return snapshot;
  }

  static Snapshot fromStore(PublishedMetricStore store, long now) {
    if (store == null) {
      return Snapshot.empty();
    }

    List<ReactMetric> declared = store.declared(SOURCE_ID);
    if (declared.isEmpty()) {
      return Snapshot.empty();
    }

    List<Entry> costs = new ArrayList<>();
    List<Entry> packetKinds = new ArrayList<>();
    double bytesPerSecond = Double.NaN;
    double packetsPerSecond = Double.NaN;
    double playerPeakBytesPerSecond = Double.NaN;
    double peakPressure = Double.NaN;

    for (ReactMetric metric : declared) {
      String key = metric.key();
      PublishedMetricStore.Reading reading = store.reading(key, now);
      if (reading == null || !Double.isFinite(reading.value())) {
        continue;
      }

      double value = Math.max(0D, reading.value());
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

  static String displayName(String id) {
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

  private record CachedSnapshot(Snapshot snapshot, long capturedAtMs) {
  }

  record Entry(String id, String displayName, double value) {
  }

  record Snapshot(
      boolean declared,
      List<Entry> costs,
      List<Entry> packetKinds,
      double bytesPerSecond,
      double packetsPerSecond,
      double playerPeakBytesPerSecond,
      double peakPressure
  ) {
    static Snapshot empty() {
      return new Snapshot(false, List.of(), List.of(), Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    double totalCostMs() {
      double total = 0D;
      for (Entry cost : costs) {
        total += cost.value();
      }
      return total;
    }
  }
}
