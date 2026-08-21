package art.arcane.react.core.integration;

import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.ReactMetricKind;
import art.arcane.react.api.metric.internal.MetricKeys;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import org.bukkit.Material;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class PublishedMetricSamplerTest {
  private static final String SOURCE = "guardianpets";
  private static final String LIVE = "guardianpets.pets.live";

  private static PublishedMetricSampler sampler(PublishedMetricStore store, ReactMetric metric) {
    List<ReactMetric> accepted = store.declare(SOURCE, List.of(metric));
    return new PublishedMetricSampler(
        MetricKeys.samplerIdFor(accepted.getFirst().key()), accepted.getFirst(), store);
  }

  @Test
  void samplerIdentityComesFromTheDeclaredMetric() {
    PublishedMetricStore store = new PublishedMetricStore();
    PublishedMetricSampler sampler = sampler(store,
        ReactMetric.gauge(LIVE, "Live Pets", " pets").withIcon(Material.BONE));

    Assertions.assertEquals("guardianpets-pets-live", sampler.getId());
    Assertions.assertEquals("Live Pets", sampler.getName());
    Assertions.assertEquals(Material.BONE, sampler.getIcon());
    Assertions.assertEquals("sampler", sampler.getConfigCategory());
  }

  @Test
  void anUnpublishedMetricRendersAsUnavailable() {
    PublishedMetricStore store = new PublishedMetricStore();
    PublishedMetricSampler sampler = sampler(store, ReactMetric.gauge(LIVE, "Live Pets", " pets"));
    sampler.start();

    Assertions.assertEquals(0D, sampler.onSample());
    Assertions.assertEquals("---", sampler.formattedValue(0D));
    Assertions.assertEquals("", sampler.formattedSuffix(0D));
  }

  @Test
  void aPublishedMetricIsSampledAndFormatted() {
    PublishedMetricStore store = new PublishedMetricStore();
    PublishedMetricSampler sampler = sampler(store,
        ReactMetric.rate(LIVE, "Pet Summons", " /min").withDecimals(2));
    sampler.start();
    store.publish(SOURCE, LIVE, 12.345D, System.currentTimeMillis(), System.currentTimeMillis());

    Assertions.assertEquals(12.345D, sampler.onSample(), 1.0E-9D);
    Assertions.assertEquals("/min", sampler.formattedSuffix(12.345D));
    Assertions.assertTrue(sampler.formattedValue(12.345D).startsWith("12.3"));
  }

  @Test
  void theLastKnownValueIsHeldWhenPublishingStops() {
    PublishedMetricStore store = new PublishedMetricStore();
    PublishedMetricSampler sampler = sampler(store, ReactMetric.gauge(LIVE, "Live Pets", " pets"));
    sampler.start();

    long now = System.currentTimeMillis();
    store.publish(SOURCE, LIVE, 9D, now, now);
    Assertions.assertEquals(9D, sampler.onSample());

    store.withdraw(SOURCE, LIVE);
    Assertions.assertEquals(9D, sampler.onSample());
    Assertions.assertEquals("---", sampler.formattedValue(9D));
  }

  @Test
  void startResetsTheHeldValue() {
    PublishedMetricStore store = new PublishedMetricStore();
    PublishedMetricSampler sampler = sampler(store, ReactMetric.gauge(LIVE, "Live Pets", " pets"));
    sampler.start();

    long now = System.currentTimeMillis();
    store.publish(SOURCE, LIVE, 9D, now, now);
    sampler.onSample();
    store.withdraw(SOURCE, LIVE);

    sampler.start();
    Assertions.assertEquals(0D, sampler.onSample());
  }

  @Test
  void loadConfigurationIsANoOpSoCurseNeverRewritesThirdPartyMetrics() {
    PublishedMetricStore store = new PublishedMetricStore();
    PublishedMetricSampler sampler = sampler(store,
        ReactMetric.gauge(LIVE, "Live Pets", " pets").withIcon(Material.BONE));

    Assertions.assertDoesNotThrow(sampler::loadConfiguration);
    Assertions.assertEquals("Live Pets", sampler.getName());
    Assertions.assertEquals(Material.BONE, sampler.getIcon());
  }

  @Test
  void suffixFallsBackToTheKindWhenNoUnitIsDeclared() {
    Assertions.assertEquals("%", PublishedMetricSampler.suffixFor(
        new ReactMetric(LIVE, ReactMetricKind.PERCENT, "", "P", Material.SLIME_BALL, 0)));
    Assertions.assertEquals("ms", PublishedMetricSampler.suffixFor(
        new ReactMetric(LIVE, ReactMetricKind.MILLIS, "", "M", Material.SLIME_BALL, 0)));
    Assertions.assertEquals("B", PublishedMetricSampler.suffixFor(
        new ReactMetric(LIVE, ReactMetricKind.BYTES, "", "B", Material.SLIME_BALL, 0)));
    Assertions.assertEquals("/s", PublishedMetricSampler.suffixFor(
        new ReactMetric(LIVE, ReactMetricKind.RATE, "", "R", Material.SLIME_BALL, 0)));
    Assertions.assertEquals("", PublishedMetricSampler.suffixFor(
        new ReactMetric(LIVE, ReactMetricKind.GAUGE, "", "G", Material.SLIME_BALL, 0)));
  }

  @Test
  void aDeclaredUnitAlwaysWinsOverTheKindDefault() {
    Assertions.assertEquals("pets", PublishedMetricSampler.suffixFor(
        new ReactMetric(LIVE, ReactMetricKind.PERCENT, " pets", "P", Material.SLIME_BALL, 0)));
  }

  @Test
  void unitAndDisplayTextAreStrippedOfSurroundingWhitespace() {
    ReactMetric metric = ReactMetric.gauge(LIVE, "  Live Pets  ", "  pets  ");
    Assertions.assertEquals("pets", metric.unit());
    Assertions.assertEquals("Live Pets", metric.displayName());
  }
}
