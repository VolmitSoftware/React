package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.internal.MetricInstaller;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.util.common.scheduling.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

class FeatureAnimusMapsTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    Mockito.when(plugin.isMonitoringOnly()).thenReturn(false);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
    MetricInstaller.install(null);
  }

  @Test
  void everyAnimusMapIsGatedOnTheAnimusSourceAndUsesTheIntegrationPrefix() {
    for (Feature feature : features()) {
      Assertions.assertTrue(feature.getId().startsWith("animus-"), feature.getId());
      Assertions.assertEquals("animus", IntegrationCapabilitySupport.integrationPluginFor(feature.getId()));
      Assertions.assertEquals(Set.of("animus"), ((CapabilityGatedFeature) feature).requiredCapabilities());
    }
  }

  @Test
  void mapsActivateOnlyWhileAnimusDeclaresItsMetricSource() {
    FeatureController controller = new FeatureController();
    PublishedMetricStore store = new PublishedMetricStore();
    MetricInstaller.install(store);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      for (Feature feature : features()) {
        Assertions.assertFalse(controller.shouldActivateFeature(feature), feature.getId());
      }
      Assertions.assertFalse(IntegrationCapabilitySupport.isCapabilityPresent(null, "animus"));

      store.declare("animus", List.of(ReactMetric.millis("animus.cost.navigation_ms", "Navigation")));
      for (Feature feature : features()) {
        Assertions.assertTrue(controller.shouldActivateFeature(feature), feature.getId());
      }
      Assertions.assertTrue(IntegrationCapabilitySupport.isCapabilityPresent(null, "animus"));

      store.withdrawSource("animus");
      for (Feature feature : features()) {
        Assertions.assertFalse(controller.shouldActivateFeature(feature), feature.getId());
      }
      Assertions.assertFalse(IntegrationCapabilitySupport.isCapabilityPresent(null, "animus"));
    }
  }

  @Test
  void overlayAddsPressureOnlyFromAnimusPressureAndMeasuredCost() {
    double baseOnly = FeatureAnimusRuntimePressureOverlay.pressureScore(100D, Double.NaN, Double.NaN);
    Assertions.assertEquals(35D, baseOnly, 1.0E-9D);
    Assertions.assertEquals(baseOnly, FeatureAnimusRuntimePressureOverlay.pressureScore(100D, 0D, 0D), 1.0E-9D);
    Assertions.assertTrue(FeatureAnimusRuntimePressureOverlay.pressureScore(100D, 60D, 0D) > baseOnly);
    Assertions.assertTrue(FeatureAnimusRuntimePressureOverlay.pressureScore(100D, 0D, 25D) > baseOnly);
    Assertions.assertEquals(
        FeatureAnimusRuntimePressureOverlay.pressureScore(0D, 100D, 0D),
        FeatureAnimusRuntimePressureOverlay.pressureScore(0D, 400D, 0D),
        1.0E-9D
    );
  }

  @Test
  void costPieBucketsSubsystemsInMicrosecondsAndFormatsMilliseconds() {
    AnimusMetricSeries.Snapshot snapshot = new AnimusMetricSeries.Snapshot(
        true,
        List.of(
            new AnimusMetricSeries.Entry("navigation", "Navigation", 4.25D),
            new AnimusMetricSeries.Entry("gui", "Gui", 0.0001D)
        ),
        List.of(),
        Double.NaN,
        Double.NaN,
        Double.NaN,
        Double.NaN
    );

    Map<String, Long> buckets = FeatureAnimusCostSharePieMap.costBuckets(snapshot);

    Assertions.assertEquals(Map.of("Navigation", 4250L), buckets);
    Assertions.assertTrue(FeatureAnimusCostSharePieMap.costBuckets(AnimusMetricSeries.Snapshot.empty()).isEmpty());
    Assertions.assertEquals("4.25", FeatureAnimusCostSharePieMap.formatMillis(4250L));
    Assertions.assertEquals("42.5", FeatureAnimusCostSharePieMap.formatMillis(42_500L));
    Assertions.assertEquals("425", FeatureAnimusCostSharePieMap.formatMillis(425_000L));
  }

  private static List<Feature> features() {
    return List.of(
        new FeatureAnimusCostSharePieMap(),
        new FeatureAnimusNetworkListMap(),
        new FeatureAnimusRuntimePressureOverlay()
    );
  }
}
