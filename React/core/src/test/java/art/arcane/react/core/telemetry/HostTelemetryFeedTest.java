package art.arcane.react.core.telemetry;

import art.arcane.react.api.sampler.Sampler;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostTelemetryFeedTest {
  @Test
  void snapshot_beforeRefresh_isEmpty() {
    HostTelemetryFeed feed = new HostTelemetryFeed(() -> HostTelemetrySnapshot::empty);

    assertTrue(feed.snapshot().readings().isEmpty());
  }

  @Test
  void refresh_buildsSourceOnce_publishesEachCapture() {
    AtomicInteger built = new AtomicInteger();
    HostTelemetrySnapshot captured = snapshotWith(HostMetric.values()[0], 42D);
    HostTelemetryFeed feed = new HostTelemetryFeed(() -> {
      built.incrementAndGet();
      return () -> captured;
    });

    feed.refresh(1_000L);
    feed.refresh(2_000L);

    assertEquals(1, built.get());
    assertSame(captured, feed.snapshot());
  }

  @Test
  void refresh_failingSource_keepsPreviousSnapshot() {
    HostTelemetrySnapshot captured = snapshotWith(HostMetric.values()[0], 7D);
    AtomicInteger calls = new AtomicInteger();
    HostTelemetryFeed feed = new HostTelemetryFeed(() -> () -> {
      if (calls.incrementAndGet() > 1) {
        throw new IllegalStateException("probe failed");
      }
      return captured;
    });

    feed.refresh(1_000L);
    feed.refresh(2_000L);

    assertSame(captured, feed.snapshot());
  }

  @Test
  void reset_clearsSnapshot_rebuildsSource() {
    AtomicInteger built = new AtomicInteger();
    HostTelemetryFeed feed = new HostTelemetryFeed(() -> {
      built.incrementAndGet();
      return () -> snapshotWith(HostMetric.values()[0], 1D);
    });
    feed.refresh(1_000L);

    feed.reset();
    assertTrue(feed.snapshot().readings().isEmpty());
    feed.refresh(2_000L);

    assertEquals(2, built.get());
  }

  @Test
  void samplers_coverRegisteredMetrics_readFeed() {
    HostMetric metric = registeredMetric();
    HostTelemetryFeed feed = new HostTelemetryFeed(() -> () -> snapshotWith(metric, 12.5D));
    List<Sampler> samplers = feed.samplers();
    long registered = Arrays.stream(HostMetric.values()).filter((HostMetric m) -> m.definition().registered()).count();
    Sampler sampler = samplers.stream().filter((Sampler s) -> s.getId().equals(metric.definition().id())).findFirst().orElseThrow();

    sampler.start();

    assertEquals(registered, samplers.size());
    assertFalse(sampler.captureReading().available());
    feed.refresh(1_000L);
    assertEquals(12.5D, sampler.captureReading().value(), 1.0E-9D);
  }

  private static HostMetric registeredMetric() {
    for (HostMetric metric : HostMetric.values()) {
      if (metric.definition().registered()) {
        return metric;
      }
    }
    throw new IllegalStateException("no registered host metric");
  }

  private static HostTelemetrySnapshot snapshotWith(HostMetric metric, double value) {
    return new HostTelemetrySnapshot(HostTelemetrySnapshot.empty().environment(),
        Map.of(metric, new HostReading(value, System.currentTimeMillis(), true, null)));
  }
}
