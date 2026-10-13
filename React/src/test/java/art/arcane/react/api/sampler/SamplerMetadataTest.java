package art.arcane.react.api.sampler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SamplerMetadataTest {
  @Test
  void unitsDescribeRawValuesRatherThanFormattedDisplays() {
    assertEquals("ratio", SamplerMetadata.forId("explosion-packet-reduction", 1_000L).unit());
    assertEquals("ratio", SamplerMetadata.forId("processor-system-load", 1_000L).unit());
    assertEquals("%", SamplerMetadata.forId("gc-time-percent", 1_000L).unit());
    assertEquals("ms", SamplerMetadata.forId("top-world-mspt", 1_000L).unit());
    assertEquals("ms", SamplerMetadata.forId("top-chunk-cost", 1_000L).unit());
    assertEquals(Sampler.Quality.ESTIMATED, SamplerMetadata.forId("top-world-mspt", 1_000L).quality());
    assertEquals("/min", SamplerMetadata.forId("tick-spike-rate", 250L).unit());
    assertEquals("calls/tick", SamplerMetadata.forId("event-handles-per-tick", 1_000L).unit());
  }
}
