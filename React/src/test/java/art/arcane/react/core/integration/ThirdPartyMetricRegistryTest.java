package art.arcane.react.core.integration;

import art.arcane.react.api.metric.ReactMetrics;
import art.arcane.react.api.metric.internal.MetricInstaller;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.core.controller.SampleController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

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

    SampleController samples = Mockito.mock(SampleController.class);
    Mockito.when(samples.getSampler("adapt-xp-rate")).thenReturn(absent);

    try (MockedStatic<SamplerHosts> hosts = Mockito.mockStatic(SamplerHosts.class)) {
      hosts.when(SamplerHosts::sampleController).thenReturn(samples);
      new ThirdPartyMetricRegistry(List::of).install();

      Assertions.assertTrue(Double.isNaN(ReactMetrics.readHostMetric("adapt-xp-rate")));
      Assertions.assertFalse(ReactMetrics.hostMetricAvailable("adapt-xp-rate"));
    }
  }

  @Test
  void availableHostSamplerPublishesItsValue() {
    Sampler present = Mockito.mock(Sampler.class);
    Mockito.when(present.isSampleAvailable()).thenReturn(true);
    Mockito.when(present.sample()).thenReturn(12.5D);

    SampleController samples = Mockito.mock(SampleController.class);
    Mockito.when(samples.getSampler("players")).thenReturn(present);

    try (MockedStatic<SamplerHosts> hosts = Mockito.mockStatic(SamplerHosts.class)) {
      hosts.when(SamplerHosts::sampleController).thenReturn(samples);
      new ThirdPartyMetricRegistry(List::of).install();

      Assertions.assertEquals(12.5D, ReactMetrics.readHostMetric("players"), 1.0E-9D);
      Assertions.assertTrue(ReactMetrics.hostMetricAvailable("players"));
    }
  }
}
