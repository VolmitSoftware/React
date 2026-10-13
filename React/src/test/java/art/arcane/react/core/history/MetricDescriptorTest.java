package art.arcane.react.core.history;

import art.arcane.react.api.sampler.Sampler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MetricDescriptorTest {
  @Test
  void unavailableLiveReplacementDoesNotEraseStoredCoverage() {
    MetricDescriptor stored = new MetricDescriptor("tick", "Tick", "ms", 1_000L, 2_000L, false, Sampler.Metadata.UNKNOWN);
    MetricDescriptor unavailable = new MetricDescriptor("tick", "Tick", "ms", 0L, 0L, true, Sampler.Metadata.UNKNOWN);

    MetricDescriptor merged = stored.merge(unavailable);

    assertEquals(1_000L, merged.firstTimestampMs());
    assertEquals(2_000L, merged.lastTimestampMs());
  }
}
