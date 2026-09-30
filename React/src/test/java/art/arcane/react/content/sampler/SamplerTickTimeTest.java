package art.arcane.react.content.sampler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplerTickTimeTest {
  private static final long MS = 1_000_000L;
  private static final long BASE = 10_000_000_000L;

  @Test
  void averagesTheServerTickWorkTime() {
    SimulatedServerTickTimes server = new SimulatedServerTickTimes();
    TickClock clock = new TickClock(() -> server);
    SamplerTickTime sampler = new SamplerTickTime(clock);
    sampler.start();

    assertFalse(sampler.isSampleAvailable());
    long at = server.ticks(clock, BASE, 20, 37_500_000L);
    clock.tick(at);

    assertTrue(sampler.isSampleAvailable());
    assertEquals(37.5D, sampler.onSample(), 1.0E-9D);
    assertEquals("ms", sampler.formattedSuffix(37.5D));
  }

  @Test
  void isUnavailableWithoutAWorkTimeSource() {
    TickClock clock = new TickClock(() -> null);
    SamplerTickTime sampler = new SamplerTickTime(clock);
    sampler.start();

    clock.tick(BASE);
    clock.tick(BASE + 50L * MS);
    clock.tick(BASE + 100L * MS);
    clock.tick(BASE + 150L * MS);
    clock.tick(BASE + 200L * MS);
    clock.tick(BASE + 250L * MS);

    assertFalse(sampler.isSampleAvailable());
    assertEquals(0D, sampler.onSample(), 1.0E-9D);
    assertEquals("ms", sampler.formattedSuffix(0D));
  }

  @Test
  void formatsSubMillisecondTickTimeWithTwoDecimals() {
    TickClock clock = new TickClock(() -> () -> new long[0]);
    SamplerTickTime sampler = new SamplerTickTime(clock);
    clock.tick(BASE);

    assertEquals("0.28 ms", sampler.format(0.28D));
    assertEquals("37.5 ms", sampler.format(37.5D));
    assertEquals("12.34 ms", sampler.format(12.34D));
    assertEquals("1.5 s", sampler.format(1500D));
  }

  @Test
  void emptyWorkTimeWindowIsUnavailable() {
    TickClock clock = new TickClock(() -> () -> new long[0]);
    SamplerTickTime sampler = new SamplerTickTime(clock);
    sampler.start();
    clock.tick(BASE);

    assertFalse(sampler.isSampleAvailable());
    assertEquals(0D, sampler.onSample(), 1.0E-9D);
  }
}
