package art.arcane.react.content.feature;

import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AnimusMetricSeriesTest {
  private static final String SOURCE = "animus";

  @Test
  void missingStoreOrUndeclaredSourceIsAnEmptyUndeclaredSnapshot() {
    long now = System.currentTimeMillis();
    AnimusMetricSeries.Snapshot noStore = AnimusMetricSeries.fromStore(null, now);
    AnimusMetricSeries.Snapshot noSource = AnimusMetricSeries.fromStore(new PublishedMetricStore(), now);

    for (AnimusMetricSeries.Snapshot snapshot : List.of(noStore, noSource)) {
      Assertions.assertFalse(snapshot.declared());
      Assertions.assertTrue(snapshot.costs().isEmpty());
      Assertions.assertTrue(snapshot.packetKinds().isEmpty());
      Assertions.assertTrue(Double.isNaN(snapshot.bytesPerSecond()));
      Assertions.assertTrue(Double.isNaN(snapshot.peakPressure()));
      Assertions.assertEquals(0D, snapshot.totalCostMs());
    }
  }

  @Test
  void declaredSourceWithoutReadingsIsDeclaredButEmpty() {
    PublishedMetricStore store = new PublishedMetricStore();
    store.declare(SOURCE, List.of(
        ReactMetric.millis("animus.cost.navigation_ms", "Navigation"),
        ReactMetric.rate("animus.net.bytes_per_second", "Bandwidth", "B/s")
    ));

    AnimusMetricSeries.Snapshot snapshot = AnimusMetricSeries.fromStore(store, System.currentTimeMillis());

    Assertions.assertTrue(snapshot.declared());
    Assertions.assertTrue(snapshot.costs().isEmpty());
    Assertions.assertTrue(Double.isNaN(snapshot.bytesPerSecond()));
  }

  @Test
  void selectsCostKindAndPressureKeysByPrefixAndRanksThem() {
    long now = System.currentTimeMillis();
    PublishedMetricStore store = publish(now, Map.ofEntries(
        Map.entry("animus.cost.presentation_ms", 1.5D),
        Map.entry("animus.cost.navigation_ms", 4.25D),
        Map.entry("animus.cost.combat_ms", 0D),
        Map.entry("animus.cost.total_ms", 99D),
        Map.entry("animus.net.bytes_per_second", 2048D),
        Map.entry("animus.net.packets_per_second", 120D),
        Map.entry("animus.net.player_peak.bytes_per_second", 900D),
        Map.entry("animus.net.kind.move.bytes_per_second", 1200D),
        Map.entry("animus.net.kind.metadata.bytes_per_second", 800D),
        Map.entry("animus.pressure.navigation", 40D),
        Map.entry("animus.pressure.combat", 140D),
        Map.entry("animus.npcs.active", 25D)
    ));

    AnimusMetricSeries.Snapshot snapshot = AnimusMetricSeries.fromStore(store, now);

    Assertions.assertTrue(snapshot.declared());
    Assertions.assertEquals(List.of("navigation", "presentation"), ids(snapshot.costs()));
    Assertions.assertEquals("Navigation", snapshot.costs().getFirst().displayName());
    Assertions.assertEquals(5.75D, snapshot.totalCostMs(), 1.0E-9D);
    Assertions.assertEquals(List.of("move", "metadata"), ids(snapshot.packetKinds()));
    Assertions.assertEquals(2048D, snapshot.bytesPerSecond());
    Assertions.assertEquals(120D, snapshot.packetsPerSecond());
    Assertions.assertEquals(900D, snapshot.playerPeakBytesPerSecond());
    Assertions.assertEquals(100D, snapshot.peakPressure());
  }

  @Test
  void nestedOrMalformedKeysAreNotMistakenForSubsystems() {
    long now = System.currentTimeMillis();
    PublishedMetricStore store = publish(now, Map.of(
        "animus.cost.rigs.extra_ms", 3D,
        "animus.cost._ms", 3D,
        "animus.net.kind.bytes_per_second", 3D,
        "animus.net.kind.a.b.bytes_per_second", 3D,
        "animus.pressure.nav.deep", 80D
    ));

    AnimusMetricSeries.Snapshot snapshot = AnimusMetricSeries.fromStore(store, now);

    Assertions.assertTrue(snapshot.costs().isEmpty());
    Assertions.assertTrue(snapshot.packetKinds().isEmpty());
    Assertions.assertTrue(Double.isNaN(snapshot.peakPressure()));
  }

  @Test
  void staleReadingsAreIgnored() {
    long now = System.currentTimeMillis();
    PublishedMetricStore store = publish(now, Map.of("animus.cost.gui_ms", 2D));

    AnimusMetricSeries.Snapshot snapshot = AnimusMetricSeries.fromStore(store, now + PublishedMetricStore.MAX_STALE_MS + 1L);

    Assertions.assertTrue(snapshot.declared());
    Assertions.assertTrue(snapshot.costs().isEmpty());
  }

  @Test
  void displayNamesAreTitleCased() {
    Assertions.assertEquals("Hotload Watch", AnimusMetricSeries.displayName("hotload_watch"));
    Assertions.assertEquals("Spawn", AnimusMetricSeries.displayName("spawn"));
    Assertions.assertEquals("", AnimusMetricSeries.displayName(" "));
  }

  private static PublishedMetricStore publish(long now, Map<String, Double> values) {
    PublishedMetricStore store = new PublishedMetricStore();
    List<ReactMetric> metrics = new ArrayList<>();
    for (String key : values.keySet()) {
      metrics.add(ReactMetric.gauge(key, key, ""));
    }
    List<ReactMetric> accepted = store.declare(SOURCE, metrics);
    for (ReactMetric metric : accepted) {
      Assertions.assertTrue(store.publish(SOURCE, metric.key(), values.get(metric.key()), now, now), metric.key());
    }
    return store;
  }

  private static List<String> ids(List<AnimusMetricSeries.Entry> entries) {
    List<String> ids = new ArrayList<>();
    for (AnimusMetricSeries.Entry entry : entries) {
      ids.add(entry.id());
    }
    return ids;
  }
}
