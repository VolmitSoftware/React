package art.arcane.react.core.telemetry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CounterRateTest {
  @Test
  void firstSampleIsZeroAndSubsequentSamplesArePerMinute() {
    CounterRate rate = new CounterRate();
    Assertions.assertEquals(0D, rate.perMinute(10L, 1_000L));
    Assertions.assertEquals(120D, rate.perMinute(12L, 2_000L));
    Assertions.assertEquals(0D, rate.perMinute(1L, 3_000L));
  }
}
