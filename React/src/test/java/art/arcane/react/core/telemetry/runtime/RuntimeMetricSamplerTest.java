package art.arcane.react.core.telemetry.runtime;

import art.arcane.react.api.sampler.Sampler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class RuntimeMetricSamplerTest {
  @Test
  void capturesOneImmutableObservationAndPreservesUnavailableReason() {
    AtomicInteger reads = new AtomicInteger();
    RuntimeMetricSampler sampler = new RuntimeMetricSampler(
        new RuntimeMetricSampler.Definition("test", "Test", "ms", 1_000L), () -> {
          reads.incrementAndGet();
          return new RuntimeMetric(4D, 12_000L, false, "disabled");
        });
    sampler.start();
    Sampler.Reading reading = sampler.captureReading();
    Assertions.assertEquals(1, reads.get());
    Assertions.assertEquals(12_000L, reading.sampledAtMs());
    Assertions.assertFalse(reading.available());
    Assertions.assertEquals("disabled", reading.unavailableReason());
    Assertions.assertEquals(Sampler.Quality.MEASURED, reading.metadata().quality());
    sampler.stop();
    Assertions.assertEquals("stopped", sampler.captureReading().unavailableReason());
  }
}
