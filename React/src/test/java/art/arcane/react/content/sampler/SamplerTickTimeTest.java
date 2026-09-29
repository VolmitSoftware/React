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
    long[] tickTimes = new long[100];
    for (int i = 0; i < tickTimes.length; i++) {
      tickTimes[i] = 37_500_000L;
    }
    TickClock clock = new TickClock(() -> tickTimes);
    SamplerTickTime sampler = new SamplerTickTime(clock);
    sampler.start();

    assertFalse(sampler.isSampleAvailable());
    clock.tick(BASE);

    assertTrue(sampler.isSampleAvailable());
    assertEquals(37.5D, sampler.onSample(), 1.0E-9D);
  }

  @Test
  void fallsBackToTickGapsWithoutServerTickTimes() {
    TickClock clock = new TickClock(() -> null);
    SamplerTickTime sampler = new SamplerTickTime(clock);
    sampler.start();

    clock.tick(BASE);
    clock.tick(BASE + 60L * MS);
    clock.tick(BASE + 100L * MS);
    clock.tick(BASE + 140L * MS);
    clock.tick(BASE + 200L * MS);
    clock.tick(BASE + 250L * MS);

    assertTrue(sampler.isSampleAvailable());
    assertEquals(50D, sampler.onSample(), 1.0E-9D);
    assertEquals("ms GAP", sampler.formattedSuffix(50D));
  }

  @Test
  void formatsSubMillisecondTickTimeWithTwoDecimals() {
    long[] tickTimes = new long[100];
    TickClock clock = new TickClock(() -> tickTimes);
    SamplerTickTime sampler = new SamplerTickTime(clock);
    clock.tick(BASE);

    assertEquals("0.28 ms", sampler.format(0.28D));
    assertEquals("37.5 ms", sampler.format(37.5D));
    assertEquals("12.34 ms", sampler.format(12.34D));
    assertEquals("1.5 s", sampler.format(1500D));
  }

  @Test
  void invalidHistoryCannotSignalPressure() {
    long[] tickTimes = new long[100];
    TickClock clock = new TickClock(() -> tickTimes);
    SamplerTickTime sampler = new SamplerTickTime(clock);
    sampler.start();
    clock.tick(BASE);

    assertFalse(sampler.isSampleAvailable());
    assertEquals(0D, sampler.onSample(), 1.0E-9D);
  }
}
