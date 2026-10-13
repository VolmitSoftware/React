package art.arcane.react.core.telemetry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ObservationMetricsTest {
  @Test
  void counterRatesUseActualElapsedTimeAndDurationRatesUseMilliseconds() {
    Assertions.assertEquals(3D, ObservationMetrics.Counter.NEARBY_QUERIES.rate(6L, 2_000_000_000L));
    Assertions.assertEquals(25D, ObservationMetrics.Counter.MAP_RENDER_NANOS.rate(50_000_000L, 2_000_000_000L));
    Assertions.assertEquals(0D, ObservationMetrics.Counter.MAP_RENDER_NANOS.rate(50_000_000L, 0L));
  }

  @Test
  void drainSeparatesObservationWindows() {
    ObservationMetrics.drain();
    ObservationMetrics.increment(ObservationMetrics.Counter.MAP_BUSY_SKIPS);
    ObservationMetrics.add(ObservationMetrics.Counter.NEARBY_CANDIDATES, 120L);
    long[] first = ObservationMetrics.drain();
    Assertions.assertEquals(1L, first[ObservationMetrics.Counter.MAP_BUSY_SKIPS.ordinal()]);
    Assertions.assertEquals(120L, first[ObservationMetrics.Counter.NEARBY_CANDIDATES.ordinal()]);
    Assertions.assertEquals(0L, ObservationMetrics.drain()[ObservationMetrics.Counter.NEARBY_CANDIDATES.ordinal()]);
  }
}
