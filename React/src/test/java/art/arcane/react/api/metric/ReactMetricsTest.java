package art.arcane.react.api.metric;

import art.arcane.react.api.metric.internal.MetricInstaller;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class ReactMetricsTest {
  private static final String SOURCE = "guardianpets";
  private static final String LIVE = "guardianpets.pets.live";

  @AfterEach
  void uninstallEverything() {
    MetricInstaller.install(null);
    MetricInstaller.installHostMetrics(null, null);
  }

  @Test
  void everyCallIsSafeAndInertWhenReactIsAbsent() {
    MetricInstaller.install(null);

    Assertions.assertFalse(ReactMetrics.available());
    Assertions.assertFalse(ReactMetrics.accepting(SOURCE));
    Assertions.assertFalse(ReactMetrics.publish(SOURCE, LIVE, 1D));
    Assertions.assertFalse(ReactMetrics.publish(SOURCE, LIVE, 1D, System.currentTimeMillis()));
    ReactMetrics.withdraw(SOURCE, LIVE);
    Assertions.assertTrue(ReactMetrics.publishedSourceIds().isEmpty());
  }

  @Test
  void publishingReachesTheBoundStore() {
    PublishedMetricStore store = new PublishedMetricStore();
    store.declare(SOURCE, List.of(ReactMetric.gauge(LIVE, "Live Pets", " pets")));
    MetricInstaller.install(store);

    Assertions.assertTrue(ReactMetrics.available());
    Assertions.assertTrue(ReactMetrics.accepting(SOURCE));
    Assertions.assertTrue(ReactMetrics.publish(SOURCE, LIVE, 42D));
    Assertions.assertEquals(42D, store.valueOr(LIVE, -1D, System.currentTimeMillis()));
    Assertions.assertEquals(Set.of(SOURCE), ReactMetrics.publishedSourceIds());
  }

  @Test
  void publishingAnUndeclaredSourceIsRefused() {
    MetricInstaller.install(new PublishedMetricStore());
    Assertions.assertFalse(ReactMetrics.accepting(SOURCE));
    Assertions.assertFalse(ReactMetrics.publish(SOURCE, LIVE, 1D));
  }

  @Test
  void withdrawClearsTheReading() {
    PublishedMetricStore store = new PublishedMetricStore();
    store.declare(SOURCE, List.of(ReactMetric.gauge(LIVE, "Live Pets", " pets")));
    MetricInstaller.install(store);

    ReactMetrics.publish(SOURCE, LIVE, 42D);
    ReactMetrics.withdraw(SOURCE, LIVE);

    Assertions.assertFalse(store.available(LIVE, System.currentTimeMillis()));
  }

  @Test
  void uninstallOnlyClearsTheMatchingStore() {
    PublishedMetricStore first = new PublishedMetricStore();
    PublishedMetricStore second = new PublishedMetricStore();

    MetricInstaller.install(first);
    MetricInstaller.uninstall(second);
    Assertions.assertSame(first, MetricInstaller.binding());

    MetricInstaller.uninstall(first);
    Assertions.assertNull(MetricInstaller.binding());
  }

  @Test
  void hostMetricsAreUnreadableUntilReactBindsThem() {
    MetricInstaller.installHostMetrics(null, null);

    Assertions.assertTrue(ReactMetrics.hostMetricKeys().isEmpty());
    Assertions.assertTrue(Double.isNaN(ReactMetrics.readHostMetric("tick-time")));
    Assertions.assertFalse(ReactMetrics.hostMetricAvailable("tick-time"));
  }

  @Test
  void reactPublishesItsOwnNumbersThroughTheSameFacade() {
    Map<String, Double> host = Map.of("tick-time", 21.5D, "entities", 1200D);
    MetricInstaller.installHostMetrics(host::keySet, key -> host.getOrDefault(key, Double.NaN));

    Assertions.assertEquals(Set.of("tick-time", "entities"), ReactMetrics.hostMetricKeys());
    Assertions.assertEquals(21.5D, ReactMetrics.readHostMetric("tick-time"));
    Assertions.assertTrue(ReactMetrics.hostMetricAvailable("entities"));
    Assertions.assertFalse(ReactMetrics.hostMetricAvailable("not-a-sampler"));
    Assertions.assertTrue(Double.isNaN(ReactMetrics.readHostMetric("not-a-sampler")));
  }

  @Test
  void hostMetricReadsRejectBlankKeysWithoutCallingTheReader() {
    boolean[] called = {false};
    MetricInstaller.installHostMetrics(Set::of, key -> {
      called[0] = true;
      return 1D;
    });

    Assertions.assertTrue(Double.isNaN(ReactMetrics.readHostMetric("  ")));
    Assertions.assertTrue(Double.isNaN(ReactMetrics.readHostMetric(null)));
    Assertions.assertFalse(called[0]);
  }

  @Test
  void aNullHostKeySupplierResultIsTreatedAsEmpty() {
    MetricInstaller.installHostMetrics(() -> null, key -> Double.NaN);
    Assertions.assertTrue(ReactMetrics.hostMetricKeys().isEmpty());
  }
}
