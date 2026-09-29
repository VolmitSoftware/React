package art.arcane.react.content.sampler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

class SamplerTickSpikeRateTest {
  private static final long MS = 1_000_000L;

  @Test
  void steadyTicksProduceNoSpikes() {
    TickClock clock = new TickClock(() -> null);
    SamplerTickSpikeRate sampler = new SamplerTickSpikeRate(clock);
    sampler.start();
    long[] jitter = {50L, 49L, 51L, 50L, 52L, 48L, 50L, 50L, 53L, 50L};
    long at = System.nanoTime() - 10_050L * MS;
    for (int i = 0; i < 200; i++) {
      at += jitter[i % jitter.length] * MS;
      clock.tick(at);
    }

    Assertions.assertTrue(sampler.isSampleAvailable());
    Assertions.assertEquals(0D, sampler.onSample(), 1.0E-9D);
  }

  @Test
  void lateTicksCountAsSpikesPerMinuteInGapMode() {
    TickClock clock = new TickClock(() -> null);
    SamplerTickSpikeRate sampler = new SamplerTickSpikeRate(clock);
    sampler.start();
    long at = System.nanoTime() - 10_050L * MS;
    for (int i = 0; i < 100; i++) {
      at += 50L * MS;
      clock.tick(at);
    }
    at += 130L * MS;
    clock.tick(at);
    for (int i = 0; i < 96; i++) {
      at += 50L * MS;
      clock.tick(at);
    }

    Assertions.assertEquals(1D, sampler.onSample(), 1.0E-9D);
    Assertions.assertEquals("1", sampler.formattedValue(1D));
    Assertions.assertEquals("2.5", sampler.formattedValue(2.5D));
    Assertions.assertEquals("SPIKE/m", sampler.formattedSuffix(1D));
  }

  @Test
  void workTimeSpikesUseTheConfiguredThreshold() throws ReflectiveOperationException {
    long[] baseline = new long[100];
    for (int i = 0; i < baseline.length; i++) {
      baseline[i] = 5L * MS;
    }
    long[][] current = {baseline};
    TickClock clock = new TickClock(() -> current[0]);
    SamplerTickSpikeRate sampler = new SamplerTickSpikeRate(clock);
    sampler.start();

    long at = System.nanoTime() - 1_000L * MS;
    clock.tick(at);
    long[] next = baseline.clone();
    next[3] = 70L * MS;
    next[4] = 40L * MS;
    current[0] = next;
    at += 500L * MS;
    clock.tick(at);

    Assertions.assertEquals(1D, sampler.onSample(), 1.0E-9D);

    setInt(sampler, "spikeThresholdMS", 100);
    Assertions.assertEquals(0D, sampler.onSample(), 1.0E-9D);

    setInt(sampler, "spikeThresholdMS", 30);
    setInt(sampler, "windowMS", 30_000);
    Assertions.assertEquals(4D, sampler.onSample(), 1.0E-9D);
  }

  @Test
  void unavailableBeforeTheClockHasHistory() {
    TickClock clock = new TickClock(() -> null);
    SamplerTickSpikeRate sampler = new SamplerTickSpikeRate(clock);
    sampler.start();

    Assertions.assertFalse(sampler.isSampleAvailable());
    Assertions.assertEquals(0D, sampler.onSample(), 1.0E-9D);
  }

  private static void setInt(Object target, String name, int value) throws ReflectiveOperationException {
    Field field = SamplerTickSpikeRate.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setInt(target, value);
  }
}
