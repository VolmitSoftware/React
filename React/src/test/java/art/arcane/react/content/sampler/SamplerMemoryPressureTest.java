package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.telemetry.HostMetric;
import art.arcane.react.core.telemetry.HostReading;
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class SamplerMemoryPressureTest {
  @Test
  void allocationUsesTheSharedCounterReadingAndItsSourceTime() {
    long nowMs = System.currentTimeMillis();
    HostTelemetrySnapshot snapshot = new HostTelemetrySnapshot(HostTelemetrySnapshot.empty().environment(),
        Map.of(HostMetric.ALLOCATION, new HostReading(512D, nowMs, true, "")));
    SamplerMemoryPressure sampler = new SamplerMemoryPressure(() -> snapshot);
    Assertions.assertEquals(512D, sampler.onSample());
    Assertions.assertEquals(nowMs, sampler.captureReading().sampledAtMs());
    Assertions.assertEquals(1_000L, sampler.metadata().intervalMs());
    Assertions.assertEquals(Sampler.Quality.ESTIMATED, sampler.metadata().quality());
    Assertions.assertEquals("bytes/s", sampler.metadata().unit());
  }

  @Test
  void allocationDoesNotInventZeroForAnUnsupportedCounter() {
    SamplerMemoryPressure sampler = new SamplerMemoryPressure(HostTelemetrySnapshot::empty);
    Assertions.assertFalse(sampler.isSampleAvailable());
    Assertions.assertFalse(sampler.captureReading().available());
    Assertions.assertEquals("warming-up", sampler.captureReading().unavailableReason());
  }
}
