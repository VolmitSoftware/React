package art.arcane.react.api.metric.internal;

import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.ReactMetricKind;
import org.bukkit.Material;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class PublishedMetricStoreTest {
  private static final String SOURCE = "guardianpets";
  private static final String LIVE = "guardianpets.pets.live";

  private static PublishedMetricStore storeWithLive() {
    PublishedMetricStore store = new PublishedMetricStore();
    store.declare(SOURCE, List.of(ReactMetric.gauge(LIVE, "Live Pets", " pets")));
    return store;
  }

  @Test
  void nothingIsAcceptedBeforeDeclaration() {
    PublishedMetricStore store = new PublishedMetricStore();
    Assertions.assertFalse(store.accepting(SOURCE));
    Assertions.assertFalse(store.publish(SOURCE, LIVE, 1D, 1000L, 1000L));
    Assertions.assertEquals(1L, store.droppedSamples());
  }

  @Test
  void declaringMakesTheSourceAcceptingAndThePublishLands() {
    PublishedMetricStore store = storeWithLive();

    Assertions.assertTrue(store.accepting(SOURCE));
    Assertions.assertTrue(store.publish(SOURCE, LIVE, 12D, 1000L, 1000L));
    Assertions.assertTrue(store.available(LIVE, 1000L));
    Assertions.assertEquals(12D, store.valueOr(LIVE, -1D, 1000L));
    Assertions.assertEquals(1L, store.acceptedSamples());
  }

  @Test
  void sourceIdsAreNormalizedOnBothSides() {
    PublishedMetricStore store = storeWithLive();
    Assertions.assertTrue(store.accepting("  GuardianPets "));
    Assertions.assertTrue(store.publish("GUARDIANPETS", LIVE, 3D, 1000L, 1000L));
  }

  @Test
  void reservedAndMalformedSourceIdsCannotDeclare() {
    PublishedMetricStore store = new PublishedMetricStore();
    Assertions.assertTrue(store.declare("react", List.of(ReactMetric.gauge("react.x", "X", ""))).isEmpty());
    Assertions.assertTrue(store.declare("adapt", List.of(ReactMetric.gauge("adapt.x", "X", ""))).isEmpty());
    Assertions.assertTrue(store.declare("Bad Id", List.of()).isEmpty());
    Assertions.assertTrue(store.sourceIds().isEmpty());
  }

  @Test
  void keysOutsideTheSourceNamespaceAreDropped() {
    PublishedMetricStore store = new PublishedMetricStore();
    List<ReactMetric> accepted = store.declare(SOURCE, List.of(
        ReactMetric.gauge(LIVE, "Live", ""),
        ReactMetric.gauge("adapt.xp", "Stolen", ""),
        ReactMetric.gauge("guardianpets.BAD", "Bad", "")));

    Assertions.assertEquals(1, accepted.size());
    Assertions.assertEquals(LIVE, accepted.getFirst().key());
  }

  @Test
  void duplicateKeysWithinOneDeclarationAreCollapsed() {
    PublishedMetricStore store = new PublishedMetricStore();
    List<ReactMetric> accepted = store.declare(SOURCE, List.of(
        ReactMetric.gauge(LIVE, "First", ""),
        ReactMetric.gauge(LIVE, "Second", "")));

    Assertions.assertEquals(1, accepted.size());
    Assertions.assertEquals("First", accepted.getFirst().displayName());
  }

  @Test
  void thirdPartyTextIsSanitizedAndTruncatedAtDeclarationTime() {
    PublishedMetricStore store = new PublishedMetricStore();
    List<ReactMetric> accepted = store.declare(SOURCE, List.of(new ReactMetric(
        LIVE, ReactMetricKind.GAUGE, "§cred", "Evil\nName" + "x".repeat(200), Material.BONE, 2)));

    ReactMetric metric = accepted.getFirst();
    Assertions.assertEquals("cred", metric.unit());
    Assertions.assertFalse(metric.displayName().contains("\n"));
    Assertions.assertTrue(metric.displayName().length() <= 48);
    Assertions.assertEquals(Material.BONE, metric.icon());
  }

  @Test
  void metricsPerSourceAreCapped() {
    PublishedMetricStore store = new PublishedMetricStore();
    List<ReactMetric> metrics = new ArrayList<>();

    for (int i = 0; i < PublishedMetricStore.MAX_METRICS_PER_SOURCE + 10; i++) {
      metrics.add(ReactMetric.gauge(SOURCE + ".m" + i, "M" + i, ""));
    }

    Assertions.assertEquals(PublishedMetricStore.MAX_METRICS_PER_SOURCE, store.declare(SOURCE, metrics).size());
  }

  @Test
  void sourceCountIsCapped() {
    PublishedMetricStore store = new PublishedMetricStore();

    for (int i = 0; i < PublishedMetricStore.MAX_SOURCES; i++) {
      String source = "source" + i;
      Assertions.assertFalse(store.declare(source, List.of(ReactMetric.gauge(source + ".m", "M", ""))).isEmpty());
    }

    Assertions.assertTrue(store.declare("overflow", List.of(ReactMetric.gauge("overflow.m", "M", ""))).isEmpty());
    Assertions.assertEquals(PublishedMetricStore.MAX_SOURCES, store.sourceIds().size());
  }

  @Test
  void anAlreadyDeclaredSourceCanRedeclareWhenTheCapIsReached() {
    PublishedMetricStore store = new PublishedMetricStore();

    for (int i = 0; i < PublishedMetricStore.MAX_SOURCES; i++) {
      String source = "source" + i;
      store.declare(source, List.of(ReactMetric.gauge(source + ".m", "M", "")));
    }

    Assertions.assertFalse(store.declare("source0", List.of(ReactMetric.gauge("source0.m2", "M2", ""))).isEmpty());
  }

  @Test
  void redeclaringDropsKeysThatAreNoLongerDeclared() {
    PublishedMetricStore store = storeWithLive();
    store.publish(SOURCE, LIVE, 5D, 1000L, 1000L);

    store.declare(SOURCE, List.of(ReactMetric.gauge(SOURCE + ".pets.dead", "Dead Pets", "")));

    Assertions.assertNull(store.metric(LIVE));
    Assertions.assertFalse(store.available(LIVE, 1000L));
    Assertions.assertFalse(store.publish(SOURCE, LIVE, 5D, 1000L, 1000L));
  }

  @Test
  void publishingAnUndeclaredKeyIsDropped() {
    PublishedMetricStore store = storeWithLive();
    Assertions.assertFalse(store.publish(SOURCE, SOURCE + ".unknown", 1D, 1000L, 1000L));
  }

  @Test
  void oneSourceCannotPublishUnderAnothersKey() {
    PublishedMetricStore store = new PublishedMetricStore();
    store.declare("alpha", List.of(ReactMetric.gauge("alpha.m", "M", "")));
    store.declare("beta", List.of(ReactMetric.gauge("beta.m", "M", "")));

    Assertions.assertFalse(store.publish("beta", "alpha.m", 9D, 1000L, 1000L));
    Assertions.assertFalse(store.available("alpha.m", 1000L));
  }

  @Test
  void nonFiniteValuesAreDropped() {
    PublishedMetricStore store = storeWithLive();
    Assertions.assertFalse(store.publish(SOURCE, LIVE, Double.NaN, 1000L, 1000L));
    Assertions.assertFalse(store.publish(SOURCE, LIVE, Double.POSITIVE_INFINITY, 1000L, 1000L));
    Assertions.assertFalse(store.available(LIVE, 1000L));
  }

  @Test
  void futureAndAncientTimestampsAreDropped() {
    PublishedMetricStore store = storeWithLive();
    long now = 1_000_000L;

    Assertions.assertFalse(store.publish(SOURCE, LIVE, 1D, now + PublishedMetricStore.MAX_FUTURE_MS + 1L, now));
    Assertions.assertFalse(store.publish(SOURCE, LIVE, 1D, now - PublishedMetricStore.MAX_STALE_MS - 1L, now));
    Assertions.assertFalse(store.publish(SOURCE, LIVE, 1D, 0L, now));
    Assertions.assertFalse(store.available(LIVE, now));
    Assertions.assertEquals(3L, store.droppedSamples());
  }

  @Test
  void aSourceThatStopsPublishingBecomesUnavailableAfterTheStaleWindow() {
    PublishedMetricStore store = storeWithLive();
    long publishedAt = 1_000_000L;
    store.publish(SOURCE, LIVE, 7D, publishedAt, publishedAt);

    Assertions.assertTrue(store.available(LIVE, publishedAt + PublishedMetricStore.MAX_STALE_MS));
    Assertions.assertFalse(store.available(LIVE, publishedAt + PublishedMetricStore.MAX_STALE_MS + 1L));
    Assertions.assertEquals(-1D, store.valueOr(LIVE, -1D, publishedAt + PublishedMetricStore.MAX_STALE_MS + 1L));
  }

  @Test
  void withdrawRemovesTheReadingButKeepsTheDeclaration() {
    PublishedMetricStore store = storeWithLive();
    store.publish(SOURCE, LIVE, 4D, 1000L, 1000L);

    store.withdraw(SOURCE, LIVE);

    Assertions.assertFalse(store.available(LIVE, 1000L));
    Assertions.assertNotNull(store.metric(LIVE));
    Assertions.assertTrue(store.publish(SOURCE, LIVE, 4D, 1000L, 1000L));
  }

  @Test
  void withdrawingASourceRemovesItsDeclarationsAndReadings() {
    PublishedMetricStore store = storeWithLive();
    store.publish(SOURCE, LIVE, 4D, 1000L, 1000L);

    store.withdrawSource(SOURCE);

    Assertions.assertFalse(store.accepting(SOURCE));
    Assertions.assertNull(store.metric(LIVE));
    Assertions.assertFalse(store.available(LIVE, 1000L));
    Assertions.assertTrue(store.sourceIds().isEmpty());
  }

  @Test
  void nullAndUnknownLookupsAreSafe() {
    PublishedMetricStore store = storeWithLive();

    Assertions.assertNull(store.metric(null));
    Assertions.assertFalse(store.available(null, 1000L));
    Assertions.assertEquals(-5D, store.valueOr(null, -5D, 1000L));
    store.withdraw(SOURCE, null);
    store.withdrawSource("never-declared");
  }

  @Test
  void clearEmptiesEverything() {
    PublishedMetricStore store = storeWithLive();
    store.publish(SOURCE, LIVE, 4D, 1000L, 1000L);

    store.clear();

    Assertions.assertTrue(store.sourceIds().isEmpty());
    Assertions.assertTrue(store.keys().isEmpty());
    Assertions.assertFalse(store.available(LIVE, 1000L));
  }
}
