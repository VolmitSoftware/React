package art.arcane.react.core.integration;

import art.arcane.react.React;
import art.arcane.react.api.metric.ReactMetrics;
import art.arcane.react.api.metric.internal.MetricInstaller;
import art.arcane.react.api.sampler.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class ThirdPartyMetricRegistryTest {

  @AfterEach
  void uninstall() {
    MetricInstaller.install(null);
    MetricInstaller.installHostMetrics(null, null);
  }

  @Test
  void unavailableHostSamplerReadsAsMissingInsteadOfZero() {
    Sampler absent = Mockito.mock(Sampler.class);
    Mockito.when(absent.isSampleAvailable()).thenReturn(false);
    Mockito.when(absent.sample()).thenReturn(0D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.sampler("adapt-xp-rate")).thenReturn(absent);
      new ThirdPartyMetricRegistry().install();

      Assertions.assertTrue(Double.isNaN(ReactMetrics.readHostMetric("adapt-xp-rate")));
      Assertions.assertFalse(ReactMetrics.hostMetricAvailable("adapt-xp-rate"));
    }
  }

  @Test
  void availableHostSamplerPublishesItsValue() {
    Sampler present = Mockito.mock(Sampler.class);
    Mockito.when(present.isSampleAvailable()).thenReturn(true);
    Mockito.when(present.sample()).thenReturn(12.5D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.sampler("players")).thenReturn(present);
      new ThirdPartyMetricRegistry().install();

      Assertions.assertEquals(12.5D, ReactMetrics.readHostMetric("players"), 1.0E-9D);
      Assertions.assertTrue(ReactMetrics.hostMetricAvailable("players"));
    }
  }
}
