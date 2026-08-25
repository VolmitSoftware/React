package art.arcane.react.core.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MetricDescriptorTest {
  @Test
  void unavailableLiveReplacementDoesNotEraseStoredCoverage() {
    MetricDescriptor stored = new MetricDescriptor("tick", "Tick", "ms", 1_000L, 2_000L, false);
    MetricDescriptor unavailable = new MetricDescriptor("tick", "Tick", "ms", 0L, 0L, true);

    MetricDescriptor merged = stored.merge(unavailable);

    assertEquals(1_000L, merged.firstTimestampMs());
    assertEquals(2_000L, merged.lastTimestampMs());
  }
}
