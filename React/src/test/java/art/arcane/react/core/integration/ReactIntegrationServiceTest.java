package art.arcane.react.core.integration;

import art.arcane.react.React;
import art.arcane.react.api.metric.internal.MetricInstaller;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class ReactIntegrationServiceTest {
  @AfterEach
  void clearHostMetrics() {
    MetricInstaller.installHostMetrics(null, null);
  }

  @Test
  void exposesDeterministicGlobalSamplerDescriptors() {
    MetricInstaller.installHostMetrics(
        () -> new LinkedHashSet<>(List.of("zeta", "unknown", "alpha")),
        key -> 0D
    );
    ReactIntegrationService service = new ReactIntegrationService();

    List<IntegrationMetricDescriptor> descriptors = new ArrayList<>(service.metricDescriptors());

    Assertions.assertEquals(
        List.of("react.sampler.alpha", "react.sampler.zeta"),
        descriptors.stream().map(IntegrationMetricDescriptor::key).toList()
    );
    IntegrationMetricDescriptor alpha = descriptors.getFirst();
    Assertions.assertEquals(IntegrationMetricType.DOUBLE, alpha.type());
    Assertions.assertEquals("", alpha.unit());
    Assertions.assertEquals(
        Map.of(
            "plugin", "react",
            "domain", "sampler",
            "scope", "global",
            "sampler-id", "alpha"
        ),
        alpha.tags()
    );
  }

  @Test
  void samplesOnlyRequestedRegisteredSamplersAsOneBatch() {
    Map<String, AtomicInteger> reads = new ConcurrentHashMap<>();
    MetricInstaller.installHostMetrics(
        () -> Set.of("alpha", "beta", "gamma"),
        key -> {
          reads.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
          return switch (key) {
            case "alpha" -> 12.5D;
            case "beta" -> 7D;
            default -> 99D;
          };
        }
    );
    ReactIntegrationService service = new ReactIntegrationService();

    Map<String, IntegrationMetricSample> samples = service.sampleMetrics(Set.of(
        "react.sampler.beta",
        "react.sampler.alpha"
    ));

    Assertions.assertEquals(
        List.of("react.sampler.alpha", "react.sampler.beta"),
        new ArrayList<>(samples.keySet())
    );
    Assertions.assertEquals(12.5D, samples.get("react.sampler.alpha").numericValue());
    Assertions.assertEquals(7D, samples.get("react.sampler.beta").numericValue());
    Assertions.assertEquals(
        samples.get("react.sampler.alpha").sampledAtMs(),
        samples.get("react.sampler.beta").sampledAtMs()
    );
    Assertions.assertEquals(1, reads.get("alpha").get());
    Assertions.assertEquals(1, reads.get("beta").get());
    Assertions.assertFalse(reads.containsKey("gamma"));
  }

  @Test
  void returnsUnavailableWithoutSamplingUnknownOrNonFiniteValues() {
    AtomicInteger reads = new AtomicInteger();
    MetricInstaller.installHostMetrics(
        () -> Set.of("non-finite"),
        key -> {
          reads.incrementAndGet();
          return Double.NaN;
        }
    );
    ReactIntegrationService service = new ReactIntegrationService();

    Map<String, IntegrationMetricSample> samples = service.sampleMetrics(Set.of(
        "react.sampler.non-finite",
        "react.sampler.missing",
        "iris.pregen-queue"
    ));

    Assertions.assertFalse(samples.get("react.sampler.non-finite").available());
    Assertions.assertEquals(
        "react-sampler-value-unavailable",
        samples.get("react.sampler.non-finite").message()
    );
    Assertions.assertEquals(
        "react-sampler-not-registered",
        samples.get("react.sampler.missing").message()
    );
    Assertions.assertEquals(
        "react-does-not-publish-this-metric",
        samples.get("iris.pregen-queue").message()
    );
    Assertions.assertEquals(1, reads.get());
  }

  @Test
  void isolatesSamplerFailuresWithinRequestedBatch() {
    MetricInstaller.installHostMetrics(
        () -> Set.of("broken", "healthy"),
        key -> {
          if ("broken".equals(key)) {
            throw new IllegalStateException("broken sampler");
          }
          return 4.25D;
        }
    );
    ReactIntegrationService service = new ReactIntegrationService();

    Map<String, IntegrationMetricSample> samples;
    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      samples = service.sampleMetrics(Set.of(
          "react.sampler.broken",
          "react.sampler.healthy"
      ));
      react.verify(() -> React.warn(
          Mockito.contains("id=broken"),
          Mockito.any(IllegalStateException.class)
      ));
    }

    Assertions.assertFalse(samples.get("react.sampler.broken").available());
    Assertions.assertEquals(
        "react-sampler-error:IllegalStateException",
        samples.get("react.sampler.broken").message()
    );
    Assertions.assertTrue(samples.get("react.sampler.healthy").available());
    Assertions.assertEquals(4.25D, samples.get("react.sampler.healthy").numericValue());
  }

  @Test
  void returnsEmptyBatchForMissingDemand() {
    AtomicInteger hostKeyReads = new AtomicInteger();
    MetricInstaller.installHostMetrics(
        () -> {
          hostKeyReads.incrementAndGet();
          return Set.of("alpha");
        },
        key -> 1D
    );
    ReactIntegrationService service = new ReactIntegrationService();

    Assertions.assertTrue(service.sampleMetrics(null).isEmpty());
    Assertions.assertTrue(service.sampleMetrics(Set.of()).isEmpty());
    Assertions.assertEquals(0, hostKeyReads.get());
  }
}
