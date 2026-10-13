package art.arcane.react.core.telemetry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class HostMetricSamplerTest {
  @Test
  void staleSourcesAreUnavailableAndDoNotReceiveNewTimestamps() {
    long sourceAt = System.currentTimeMillis() - 60_000L;
    HostTelemetrySnapshot snapshot = new HostTelemetrySnapshot(HostTelemetrySnapshot.empty().environment(),
        Map.of(HostMetric.NETWORK_RECEIVE, new HostReading(123D, sourceAt, true, "")));
    HostMetricSampler sampler = new HostMetricSampler(HostMetric.NETWORK_RECEIVE, () -> snapshot);
    sampler.start();
    Assertions.assertFalse(sampler.isSampleAvailable());
    Assertions.assertEquals("stale", sampler.captureReading().unavailableReason());
    Assertions.assertEquals(sourceAt, sampler.captureReading().sampledAtMs());
    Assertions.assertEquals(10_000L, sampler.metadata().intervalMs());
    Assertions.assertEquals("bytes/s", sampler.metadata().unit());
  }

  @Test
  void eventDerivedFootprintsDoNotExpireBetweenCollections() {
    long sourceAt = System.currentTimeMillis() - 3_600_000L;
    HostReading reading = new HostReading(123D, sourceAt, true, "");
    Assertions.assertTrue(reading.fresh(HostMetric.GC_AFTER, System.currentTimeMillis()).available());
    Assertions.assertEquals(0L, HostMetric.GC_AFTER.metadata().intervalMs());
  }

  @Test
  void oneSnapshotSuppliesTheValueAndTimestamp() {
    long sourceAt = System.currentTimeMillis();
    HostTelemetrySnapshot first = new HostTelemetrySnapshot(HostTelemetrySnapshot.empty().environment(),
        Map.of(HostMetric.HEAP_MAX, new HostReading(123D, sourceAt, true, "")));
    AtomicReference<HostTelemetrySnapshot> source = new AtomicReference<>(first);
    HostMetricSampler sampler = new HostMetricSampler(HostMetric.HEAP_MAX, () -> source.getAndSet(HostTelemetrySnapshot.empty()));
    sampler.start();
    Assertions.assertEquals(123D, sampler.captureReading().value());
    Assertions.assertFalse(sampler.captureReading().available());
  }
}
