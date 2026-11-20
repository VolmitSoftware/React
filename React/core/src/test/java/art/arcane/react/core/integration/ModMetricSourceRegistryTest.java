package art.arcane.react.core.integration;

import art.arcane.react.api.metric.ReactMetrics;
import art.arcane.react.api.metric.ReactModMetric;
import art.arcane.react.api.metric.ReactModMetricSource;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.core.controller.SampleController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModMetricSourceRegistryTest {
  @Test
  void modSourceBecomesASamplerAndAcceptsPublishedValues() {
    ReactModMetricSource source = new ReactModMetricSource() {
      @Override
      public String sourceId() {
        return "pets";
      }

      @Override
      public List<ReactModMetric> metrics() {
        return List.of(ReactModMetric.gauge("pets.live", "Live Pets", "").withIcon("minecraft:bone"));
      }
    };
    SampleController samples = Mockito.mock(SampleController.class);
    Mockito.when(samples.registerSampler(Mockito.any())).thenReturn(true);
    ThirdPartyMetricRegistry registry = new ThirdPartyMetricRegistry(
        () -> List.of(new ThirdPartyMetricRegistry.Registration(source, "petmod")));

    try (MockedStatic<SamplerHosts> hosts = Mockito.mockStatic(SamplerHosts.class)) {
      hosts.when(SamplerHosts::sampleController).thenReturn(samples);
      registry.install();
      assertFalse(ReactMetrics.accepting("pets"));

      assertEquals(1, registry.reconcile(1_000L));

      ArgumentCaptor<Sampler> registered = ArgumentCaptor.forClass(Sampler.class);
      Mockito.verify(samples).registerSampler(registered.capture());
      assertEquals("minecraft:bone", registered.getValue().getIcon());
      assertTrue(ReactMetrics.accepting("pets"));
      assertTrue(ReactMetrics.publish("pets", "pets.live", 7D));
      assertEquals(7D, registry.store().valueOr("pets.live", 0D, System.currentTimeMillis()));
    } finally {
      registry.uninstall();
    }
  }
}
